@file:OptIn(ExperimentalOpenCCApi::class)
package io.github.opencckmp

import java.io.File
import kotlin.test.*

class UpstreamAnnotationTest {
    private val root = File(System.getProperty("opencc.projectRoot") ?: error("Run upstreamConformance"))
    private val source = File(root, ".cache/upstream/OpenCC-025f371dc76b598d77384fbdab90c937471844d8")
    private val data = File(root, ".cache/oracle/data")
    private fun hex(text: String) = UTF8Util.encode(text).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun engine(config: File): Converter = Config(ResourceProvider { name ->
        val direct = File(name)
        val fallback = File(data, direct.name)
        when {
            direct.isFile -> direct.readBytes()
            fallback.isFile -> fallback.readBytes()
            else -> throw FileNotFound(name)
        }
    }).newFromFile(config.absolutePath)

    private fun compare(config: File, converter: Converter, text: String) {
        val input = File(root, ".cache/probes/annotation-input.txt").apply { writeText(text) }
        val process = ProcessBuilder(File(root, ".cache/probes/annotation_probe").absolutePath,
            config.absolutePath, input.absolutePath, data.absolutePath).start()
        val expected = process.inputStream.bufferedReader().readLines()
        val errors = process.errorStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), errors)
        val result = converter.convertWithAmbiguities(text)
        val actual = buildList {
            add("OUTPUT\t" + hex(result.output))
            add("ANALYZED\t" + if (result.analyzed) "1" else "0")
            for (value in result.sources) add("SOURCE\t" + hex(value))
            for (span in result.ambiguities) {
                val offset = UTF8Util.encode(result.output.substring(0, span.outputOffset)).size
                val length = UTF8Util.encode(result.output.substring(span.outputOffset, span.outputOffset + span.outputLength)).size
                add("SPAN\t$offset\t$length\t${span.sourceIndex}")
            }
            for (candidate in converter.getAllConversions(text)) add("CANDIDATE\t" + hex(candidate))
        }
        assertEquals(expected, actual, "${config.name}/$text")
        val mapped = converter.convertWithMap(text)
        assertEquals(result.output, mapped.output)
        assertEquals(text.length, mapped.map.lastOrNull()?.sourceEnd ?: 0)
        assertEquals(mapped.output.length, mapped.map.lastOrNull()?.targetEnd ?: 0)
    }

    @Test fun allBuiltinConfigsMatchNativeCandidatesAndAmbiguities() {
        val samples = listOf("", "里", "面", "头发", "里", "⿱艹里", "𠀀里😀面", "软件鼠标内存条", "头发干燥 里面复盘", "文丑文丑")
        var count = 0
        for (config in File(source, "data/config").listFiles().orEmpty().filter { it.extension == "json" && !it.name.endsWith(".schema.json") }) {
            val converter = engine(config)
            for (text in samples) { compare(config, converter, text); count++ }
        }
        assertEquals(160, count)
        println("Native candidate/ambiguity and mapped-output comparisons: $count built-in cases")
    }

    @Test fun inlineNormalizedNestedGroupExpansionAndDeletionMatchNative() {
        val files = mapOf("straddle.txt" to "a\tx\nb\ty z\n", "candidates.txt" to "里\t里 裏\n",
            "groups.txt" to "ab\tY Z\nx\tx\n", "deletion.txt" to "x\t y\n")
        for ((name, text) in files) File(root, ".cache/probes/$name").writeText(text)
        val configurations = listOf(
            """{"conversion_chain":[{"dict":{"type":"text","file":"straddle.txt"}},{"dict":{"type":"inline","entries":{"xy":"Q"}}}]}""",
            """{"normalization":[{"dict":{"type":"inline","entries":{"里":"里"}}}],"conversion_chain":[{"dict":{"type":"text","file":"candidates.txt"}},{"dict":{"type":"inline","entries":{"裏":"裡"}}}]}""",
            """{"conversion_chain":[{"dict":{"type":"group","match_policy":"union","dicts":[{"type":"inline","entries":{"a":"X"}},{"type":"group","dicts":[{"type":"text","file":"groups.txt"}]}]}},{"dict":{"type":"text","file":"deletion.txt"}}]}""",
            """{"conversion_chain":[{"dict":{"type":"inline","entries":{"a":"literal value with spaces"}}}]}"""
        )
        val samples = listOf("", "ab", "aaaxbbb", "里里", "a😀b", "x", "xx", "ax")
        for ((index, json) in configurations.withIndex()) {
            val config = File(root, ".cache/probes/annotation-$index.json").apply { writeText(json) }
            val converter = engine(config)
            for (text in samples) compare(config, converter, text)
        }
    }

    @Test fun streamingSourceIndexesSpansWindowsAndFinalFlushMatchNative() {
        val config = File(source, "data/config/s2t.json")
        val converter = engine(config)
        val text = "头发干燥 里面复盘 软件鼠标内存条 😀 文丑 里 里里面面 ⿰龙马 ⿱艹里".repeat(3)
        val input = File(root, ".cache/probes/annotation-stream.txt").apply { writeText(text) }
        val process = ProcessBuilder(File(root, ".cache/probes/annotation_probe").absolutePath,
            config.absolutePath, input.absolutePath, data.absolutePath, "stream").start()
        val expected = process.inputStream.bufferedReader().readLines()
        val errors = process.errorStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), errors)
        val stream = AmbiguityStream(converter)
        val actual = mutableListOf<String>()
        fun emit(chunk: AmbiguityStream.Chunk) {
            actual.add("OUTPUT\t" + hex(chunk.output))
            actual.add("ANALYZED\t" + if (chunk.analyzed) "1" else "0")
            chunk.newSources.forEach { actual.add("SOURCE\t" + hex(it)) }
            for (span in chunk.ambiguities) {
                val offset = UTF8Util.encode(chunk.output.substring(0, span.outputOffset)).size
                val length = UTF8Util.encode(chunk.output.substring(span.outputOffset, span.outputOffset + span.outputLength)).size
                actual.add("SPAN\t$offset\t$length\t${span.sourceIndex}")
            }
        }
        var start = 0
        while (start < text.length) {
            var end = start
            repeat(3) { if (end < text.length) end = UTF8Util.nextIndex(text, end) }
            emit(stream.convertChunk(text.substring(start, end)))
            start = end
        }
        emit(stream.finish())
        actual.add("COUNT\t${stream.sourceCount}")
        assertEquals(expected, actual)
    }
}
