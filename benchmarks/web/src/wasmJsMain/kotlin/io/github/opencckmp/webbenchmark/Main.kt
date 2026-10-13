@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package io.github.opencckmp.webbenchmark

import io.github.opencckmp.*
import io.github.opencckmp.platform.BrowserResourceProvider
import kotlin.coroutines.*
import kotlin.js.*

@JsFun("() => performance.now()") private external fun now(): Double
@JsFun("() => location.origin") private external fun origin(): String
@JsFun("() => performance.memory ? performance.memory.usedJSHeapSize : -1") private external fun heapBytes(): Double
@JsFun("(value) => { globalThis.openccBenchmarkReport = value; document.body.textContent = value; }") private external fun publish(value: String)

private fun quoted(value: String): String = buildString {
    append('"')
    for (c in value) when (c) {
        '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
        else -> if (c.code < 32) append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
    }
    append('"')
}

fun main() {
    suspend { runBenchmark() }.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) {
            result.exceptionOrNull()?.let { publish("{\"status\":\"error\",\"error\":" + quoted(it.toString()) + "}") }
        }
    })
}

private suspend fun runBenchmark() {
    val start = now()
    var maximumSampledHeap = heapBytes()
    val host = BrowserResourceProvider(origin())
    val text = UTF8Util.decode(host.read("benchmark-input.txt"))
    val expected = UTF8Util.decode(host.read("benchmark-expected.txt"))
    check(text.length == 1000)
    val fixtureReady = now()
    maximumSampledHeap = maxOf(maximumSampledHeap, heapBytes())
    val resourceRows = mutableListOf<String>()
    val resources = BrowserResourceProvider(origin() + "/opencc")
    val provider = AsyncResourceProvider { name ->
        val resourceStart = now(); val bytes = resources.read(name); val elapsed = now() - resourceStart
        maximumSampledHeap = maxOf(maximumSampledHeap, heapBytes())
        resourceRows.add("{\"name\":" + quoted(name) + ",\"bytes\":" + bytes.size + ",\"elapsed_ms\":" + elapsed + "}")
        bytes
    }
    val preparationStart = now()
    val converter = prepareConfig("s2t.json", provider)
    val prepared = now()
    val firstStart = now(); val first = converter.convert(text); val firstMs = now() - firstStart
    check(first == expected)
    val ready = now()
    val samples = mutableListOf<Double>()
    maximumSampledHeap = maxOf(maximumSampledHeap, heapBytes())
    repeat(100) { check(converter.convert(text) == expected) }
    repeat(300) {
        val conversionStart = now(); val output = converter.convert(text); val elapsed = now() - conversionStart
        samples.add(elapsed)
        // Complete equality and heap sampling are outside the conversion interval.
        check(output == expected)
        maximumSampledHeap = maxOf(maximumSampledHeap, heapBytes())
    }
    publish("{\"status\":\"pass\",\"config\":\"s2t\",\"input_characters\":1000,\"output_verified\":true," +
        "\"fixture_fetch_ms\":" + (fixtureReady - start) + ",\"preparation_ms\":" + (prepared - preparationStart) +
        ",\"first_conversion_ms\":" + firstMs + ",\"navigation_to_ready_ms\":" + ready +
        ",\"main_to_ready_ms\":" + (ready - start) + ",\"warmup_calls\":100,\"samples_ms\":[" + samples.joinToString(",") +
        "],\"resources\":[" + resourceRows.joinToString(",") + "],\"maximum_sampled_js_heap_bytes\":" + maximumSampledHeap + "}")
}
