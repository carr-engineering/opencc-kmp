package io.github.opencckmp.jieba

import io.github.opencckmp.*
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class UpstreamJiebaTest {
    @Test fun pinnedSegmentationAndAllExperimentalConfigurations() {
        val root = File(System.getProperty("opencc.projectRoot") ?: error("Run upstreamJiebaConformance"))
        val directory = File(root, ".cache/conformance/jieba")
        val bundle = File(directory, "opencc")
        val rows = File(directory, "cases.tsv").readLines().map { it.split('\t') }
        val model = File(bundle, "jieba_dict/hmm_model.utf8").readText()
        val mismatches = mutableListOf<String>()
        for (profile in listOf("merged", "text", "text-base")) {
            val segmenter = if (profile == "merged") JiebaSegmentation(MarisaDict.fromBytes(File(bundle, "jieba_dict/jieba_merged.ocd2").readBytes()), model)
                else JiebaSegmentation.fromText(File(bundle, "jieba_dict/jieba.dict.utf8").readText(), model,
                    if (profile == "text") listOf(File(bundle, "jieba_dict/user.dict.utf8").readText()) else emptyList())
            for (row in rows.filter { it[0] == "S" && it[1] == profile }) {
                val input = bytes(row[2])
                val expected = if (row[3].toInt() == 0) emptyList() else row[4].split(',')
                val actual = segmenter.segmentUtf8(input).map { hex(it) }
                if (actual != expected) mismatches.add("segment/$profile/${row[2]}: $expected != $actual")
                val text = runCatching { UTF8Util.decode(input) }.getOrNull()
                if (text != null && segmenter.segment(text).map { hex(UTF8Util.encode(it)) } != expected)
                    mismatches.add("String segment/$profile/${row[2]}")
            }
            if (profile == "merged") {
                val sharedCases = rows.filter { it[0] == "S" && it[1] == profile }.take(64)
                val threads = Executors.newFixedThreadPool(8)
                try {
                    threads.invokeAll((1..8).map { Callable {
                        for (row in sharedCases) {
                            val expected = if (row[3].toInt() == 0) emptyList() else row[4].split(',')
                            assertEquals(expected, segmenter.segmentUtf8(bytes(row[2])).map { hex(it) })
                        }
                    } }).forEach { it.get() }
                } finally { threads.shutdownNow() }
            }
        }
        val provider = ResourceProvider { name ->
            val file = File(bundle, name)
            if (!file.isFile) throw FileNotFound(name)
            file.readBytes()
        }
        for (group in rows.filter { it[0] == "C" }.groupBy { it[1] to it[2].toBooleanStrict() }) {
            val converter = Config(provider, segmentationFactories = mapOf("jieba" to JiebaSegmentationFactory()))
                .newFromFile(group.key.first, ConfigLoadOptions(group.key.second))
            for (row in group.value) {
                val input = bytes(row[3])
                val expected = row[5]
                fun check(result: Result<String>, operation: String) {
                    if (row[4] == "OK") {
                        if (result.getOrNull() != expected) mismatches.add("$operation/${group.key}/${row[3]}: $expected != $result")
                    } else {
                        val kind = when (val error = result.exceptionOrNull()) {
                            is InvalidUTF8 -> "InvalidUTF8"; is OpenCCException -> "OpenCCException"; null -> "OK"; else -> error::class.simpleName
                        }
                        if (kind != row[5]) mismatches.add("$operation/${group.key}/${row[3]}: ${row[5]} != $kind")
                    }
                }
                check(runCatching { hex(converter.convertUtf8(input)) }, "byte")
                val text = runCatching { UTF8Util.decode(input) }.getOrNull()
                if (text != null) {
                    check(runCatching { hex(UTF8Util.encode(converter.convert(text))) }, "String")
                    check(runCatching {
                        val mapped = converter.convertWithMap(text)
                        if (text.isNotEmpty() && mapped.map.lastOrNull()?.sourceEnd != text.length) mismatches.add("map coverage/${group.key}/${row[3]}")
                        hex(UTF8Util.encode(mapped.output))
                    }, "map")
                }
            }
            for (row in rows.filter { it[0] == "T" && it[1] == group.key.first && it[2].toBooleanStrict() == group.key.second }) {
                val input = bytes(row[5])
                val stream = Utf8ConverterStream(converter, row[3].toInt())
                val stride = row[4].toInt()
                val chunks = runCatching {
                    val output = mutableListOf<String>()
                    var start = 0
                    while (start < input.size) {
                        output.add(hex(stream.convertChunk(input.copyOfRange(start, minOf(start + stride, input.size)))))
                        start += stride
                    }
                    output.add(hex(stream.finish()))
                    output
                }
                if (row[6] == "OK") {
                    val expected = row[8].split(',')
                    if (chunks.getOrNull() != expected || expected.size != row[7].toInt())
                        mismatches.add("stream/${group.key}/keep=${row[3]}/stride=$stride/${row[5]}: $expected != $chunks")
                } else {
                    val kind = when (val error = chunks.exceptionOrNull()) {
                        is InvalidUTF8 -> "InvalidUTF8"; is OpenCCException -> "OpenCCException"; null -> "OK"; else -> error::class.simpleName
                    }
                    if (kind != row[7]) mismatches.add("stream/${group.key}/keep=${row[3]}/stride=$stride/${row[5]}: ${row[7]} != $kind")
                }
            }
        }
        File(directory, "differences.txt").writeText(mismatches.joinToString("\n"))
        assertTrue(rows.count { it[0] == "C" } >= 5600)
        assertTrue(mismatches.isEmpty(), "${mismatches.size} Jieba differences:\n${mismatches.take(30).joinToString("\n")}")
        println("Pinned native Jieba operations compared: ${rows.size}")
    }
    private fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
