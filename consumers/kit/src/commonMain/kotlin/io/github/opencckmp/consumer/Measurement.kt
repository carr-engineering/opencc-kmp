package io.github.opencckmp.consumer

import kotlin.coroutines.*

internal fun interface FixtureSession { fun convert(text: String): String }
internal expect fun clockMillis(): Double
internal expect fun memorySnapshotBytes(): Double?
internal expect fun publishReport(report: String)
internal expect suspend fun readAsyncResource(name: String): ByteArray

internal fun runMeasurement(done: (String) -> Unit) {
    val start = clockMillis()
    suspend {
        val session = prepareExample(::readAsyncResource)
        val loadMs = clockMillis() - start
        val output = session.convert("汉字转换")
        check(output == "漢字轉換")
        val firstConversionMs = clockMillis() - start
        val input = "汉字转换".repeat(250)
        val expected = "漢字轉換".repeat(250)
        repeat(10) { check(session.convert(input) == expected) }
        val times = DoubleArray(40)
        var memory = memorySnapshotBytes()
        for (index in times.indices) {
            val before = clockMillis()
            check(session.convert(input) == expected)
            times[index] = clockMillis() - before
            memorySnapshotBytes()?.let { memory = maxOf(memory ?: 0.0, it) }
        }
        times.sort()
        """{"status":"pass","profile":"$fixtureProfile","output":"$output","load_ms":$loadMs,"first_conversion_ms":$firstConversionMs,"convert_1000_p50_ms":${times[19]},"convert_1000_p95_ms":${times[37]},"sampled_memory_bytes":${memory ?: "null"}}"""
    }.startCoroutine(object : Continuation<String> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<String>) {
            result.fold(done) { error ->
                val message = error.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                done("""{"status":"fail","message":"$message"}""")
            }
        }
    })
}

fun main() { runMeasurement(::publishReport) }
