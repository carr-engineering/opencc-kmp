package io.github.opencckmp.tools

import io.github.opencckmp.*
import java.io.File
import kotlin.random.Random
import kotlin.test.*

class UpstreamWriterTest {
    private val root = File(System.getProperty("opencc.projectRoot") ?: error("Run upstreamWriterConformance"))
    private fun identical(expected: ByteArray, actual: ByteArray, name: String) {
        val mismatch = (0 until minOf(expected.size, actual.size)).firstOrNull { expected[it] != actual[it] }
        assertTrue(expected.contentEquals(actual), "$name: sizes ${expected.size}/${actual.size}; first mismatch $mismatch")
    }

    @Test fun everyDartsDictionaryIsByteIdenticalToUpstream() {
        var count = 0
        for (file in File(root, ".cache/conformance/text").listFiles().orEmpty().filter { it.extension == "txt" }.sorted()) {
            val dictionary = TextDict.fromBytes(file.readBytes())
            val encoded = DictionaryWriters.ocd(dictionary)
            val upstream = File(root, ".cache/conformance/ocd/${file.nameWithoutExtension}.ocd")
            identical(upstream.readBytes(), encoded, file.name)
            val generated = File(root, ".cache/conformance/kotlin-${file.nameWithoutExtension}.ocd").apply { writeBytes(encoded) }
            val exported = File(root, ".cache/conformance/kotlin-export.txt")
            val process = ProcessBuilder(File(root, ".cache/oracle/src/tools/opencc_dict").absolutePath,
                "-i", generated.absolutePath, "-o", exported.absolutePath, "-f", "ocd", "-t", "text")
                .redirectErrorStream(true).start()
            val messages = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), messages)
            identical(DictionaryWriters.text(dictionary), exported.readBytes(), file.name + " native read")
            count++
        }
        assertEquals(22, count)
        println("Pure Kotlin Darts writer: $count byte-identical files verified by native reader")
    }

    @Test fun everyMarisaDictionaryIsByteIdenticalToUpstream() {
        var count = 0
        for (file in File(root, ".cache/conformance/text").listFiles().orEmpty().filter { it.extension == "txt" }.sorted()) {
            val dictionary = TextDict.fromBytes(file.readBytes())
            val encoded = DictionaryWriters.ocd2(dictionary)
            val generated = File(root, ".cache/conformance/kotlin-${file.nameWithoutExtension}.ocd2").apply { writeBytes(encoded) }
            val upstream = File(root, ".cache/oracle/data/${file.nameWithoutExtension}.ocd2")
            identical(upstream.readBytes(), encoded, file.name)
            val exported = File(root, ".cache/conformance/kotlin-marisa-export.txt")
            val process = ProcessBuilder(File(root, ".cache/oracle/src/tools/opencc_dict").absolutePath,
                "-i", generated.absolutePath, "-o", exported.absolutePath, "-f", "ocd2", "-t", "text")
                .redirectErrorStream(true).start()
            val messages = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), messages)
            assertEquals(dictionary.lexicon.entries.map { it.toString() }, TextDict.fromBytes(exported.readBytes()).lexicon.entries.map { it.toString() })
            count++
        }
        assertEquals(22, count)
        println("Pure Kotlin MARISA writer: $count byte-identical files verified by native reader")
    }

    @Test fun customDictionariesHaveExactNativeOutput() {
        val random = Random(142)
        val symbols = listOf("a", "b", "c", "汉", "词", "😀", "𠀀", "神", "_", "0")
        val samples = listOf(emptyList(), listOf(DictEntry("", listOf("empty")))) + List(24) { round ->
            val keys = mutableSetOf<String>()
            repeat(20 + round * 7) {
                keys.add(buildString { repeat(random.nextInt(1, 18)) { append(symbols.random(random)) } })
            }
            keys.mapIndexed { index, key -> DictEntry(key, listOf("v$index", "备选$index")) }
        }
        for ((index, entries) in samples.withIndex()) {
            val dictionary = TextDict(Lexicon(entries).sorted())
            val input = File(root, ".cache/conformance/random.txt").apply { writeBytes(DictionaryWriters.text(dictionary)) }
            for (format in listOf(DictionaryFormat.Ocd2, DictionaryFormat.Ocd)) {
                // Upstream's Darts root validator does not accept an empty key.
                if (format == DictionaryFormat.Ocd && entries.any { it.key.isEmpty() }) continue
                val output = File(root, ".cache/conformance/random.${format.formatName}")
                val process = ProcessBuilder(File(root, ".cache/oracle/src/tools/opencc_dict").absolutePath,
                    "-i", input.absolutePath, "-o", output.absolutePath, "-f", "text", "-t", format.formatName).redirectErrorStream(true).start()
                val messages = process.inputStream.bufferedReader().readText()
                assertEquals(0, process.waitFor(), messages)
                identical(output.readBytes(), DictionaryConverter.write(dictionary, format), "custom $index/${format.formatName}")
            }
        }
    }

    @Test fun mergedJiebaDictionaryMatchesNativeBuild() {
        val source = File(root, ".cache/upstream/OpenCC-025f371dc76b598d77384fbdab90c937471844d8/plugins/jieba/deps/cppjieba/dict")
        val merged = DictionaryConverter.load(listOf(File(source, "jieba.dict.utf8").readBytes(),
            File(source, "user.dict.utf8").readBytes()), DictionaryFormat.CppJiebaUtf8)
        val actual = DictionaryWriters.ocd2(merged)
        identical(File(root, ".cache/oracle/plugins/jieba/jieba_dict/jieba_merged.ocd2").readBytes(), actual, "merged Jieba")
        println("Merged Jieba dictionary: ${merged.lexicon.size} entries and exact native image verified")
    }
}
