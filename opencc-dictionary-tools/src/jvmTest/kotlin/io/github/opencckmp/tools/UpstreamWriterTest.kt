package io.github.opencckmp.tools

import io.github.opencckmp.*
import java.io.File
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
}
