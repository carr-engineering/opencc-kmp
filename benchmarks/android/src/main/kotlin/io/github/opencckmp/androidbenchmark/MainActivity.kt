package io.github.opencckmp.androidbenchmark

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import io.github.opencckmp.Config
import io.github.opencckmp.platform.AndroidAssetResourceProvider
import java.io.File
import java.security.MessageDigest
import kotlin.math.ceil
import org.json.JSONObject

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val created = SystemClock.elapsedRealtimeNanos()
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Measuring OpenCC published s2t resources…" })
        Thread {
            try { measure(created) }
            catch (error: Throwable) { Log.e("OpenCCBenchmark", "FAIL run_token=" + intent.getStringExtra("run_token"), error) }
        }.start()
    }

    private fun hash(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun memory(): JSONObject {
        val runtime = Runtime.getRuntime()
        val status = File("/proc/self/status").readLines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.first() in listOf("VmHWM:", "VmRSS:")) parts[0].removeSuffix(":") to parts[1].toLong() * 1024 else null
        }.toMap()
        return JSONObject().put("vm_hwm_bytes", status["VmHWM"] ?: JSONObject.NULL)
            .put("vm_rss_bytes", status["VmRSS"] ?: JSONObject.NULL)
            .put("java_heap_used_bytes", runtime.totalMemory() - runtime.freeMemory())
            .put("java_heap_committed_bytes", runtime.totalMemory())
            .put("java_heap_max_bytes", runtime.maxMemory())
            .put("native_heap_allocated_bytes", Debug.getNativeHeapAllocatedSize())
    }

    private fun thermal(): JSONObject {
        val power = getSystemService(POWER_SERVICE) as PowerManager
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return JSONObject().put("status", if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else JSONObject.NULL)
            .put("battery_temperature_tenths_c", battery?.getIntExtra("temperature", -1) ?: JSONObject.NULL)
            .put("battery_level", battery?.getIntExtra("level", -1) ?: JSONObject.NULL)
            .put("battery_plugged", battery?.getIntExtra("plugged", -1) ?: JSONObject.NULL)
            .put("power_save_mode", power.isPowerSaveMode)
    }

    private fun measure(created: Long) {
        val text = assets.open("benchmark-input.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val expected = assets.open("benchmark-expected.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val metadata = assets.open("benchmark-reference.json").bufferedReader().use { JSONObject(it.readText()) }
        check(text.length == 1000)
        check(hash(text) == metadata.getString("input_sha256"))
        check(hash(expected) == metadata.getString("expected_sha256"))
        val before = memory()
        val thermalBefore = thermal()
        val start = SystemClock.elapsedRealtimeNanos()
        val converter = Config(AndroidAssetResourceProvider(assets)).newFromFile("s2t.json")
        val loaded = SystemClock.elapsedRealtimeNanos()
        val first = converter.convert(text)
        val converted = SystemClock.elapsedRealtimeNanos()
        check(first == expected)
        repeat(100) { check(converter.convert(text) == expected) }
        val samples = LongArray(300)
        for (index in samples.indices) {
            val sampleStart = SystemClock.elapsedRealtimeNanos()
            val output = converter.convert(text)
            samples[index] = SystemClock.elapsedRealtimeNanos() - sampleStart
            check(output == expected) // Complete native output comparison outside the measured interval.
        }
        samples.sort()
        fun percentile(value: Double) = samples[(ceil(value * samples.size).toInt() - 1).coerceIn(samples.indices)]
        val report = JSONObject().put("schema_version", 1)
            .put("run_token", intent.getStringExtra("run_token") ?: "manual-launch")
            .put("coordinates", BuildConfig.OPENCC_COORDINATES)
            .put("pid", android.os.Process.myPid()).put("config", "s2t")
            .put("input_utf16_units", text.length).put("input_utf8_bytes", text.toByteArray(Charsets.UTF_8).size)
            .put("input_sha256", metadata.getString("input_sha256"))
            .put("expected_sha256", metadata.getString("expected_sha256"))
            .put("output_sha256", hash(first)).put("output_matches_native", true)
            .put("first_load_ns", loaded - start).put("first_conversion_ns", converted - loaded)
            .put("first_load_and_conversion_ns", converted - start)
            .put("activity_create_to_first_conversion_ns", converted - created)
            .put("warm_1000", JSONObject().put("warmup_calls", 100).put("samples", samples.size)
                .put("p50_ns", percentile(0.50)).put("p95_ns", percentile(0.95))
                .put("min_ns", samples.first()).put("max_ns", samples.last()).put("mean_ns", samples.average())
                .put("quantile", "nearest-rank"))
            .put("memory_before", before).put("memory_after", memory())
            .put("thermal_before", thermalBefore).put("thermal_after", thermal())
            .put("device", JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("fingerprint", Build.FINGERPRINT).put("sdk", Build.VERSION.SDK_INT)
                .put("abis", Build.SUPPORTED_ABIS.joinToString(",")))
            .put("scope", "Fresh application process, locally packaged resources; OS file caches may be warm. VmHWM covers process lifetime; heap values are snapshots without forced GC.")
        Log.i("OpenCCBenchmark", "RESULT " + report.toString())
        runOnUiThread { setContentView(TextView(this).apply { this.text = "PASS\n" + first.take(100) }) }
    }
}
