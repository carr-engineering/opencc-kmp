package io.github.opencckmp

import java.io.File
import kotlin.test.*

class UpstreamBinaryFormatTest {
    private val root = File(System.getProperty("opencc.projectRoot") ?: error("Run upstreamConformance"))

    @Test fun everyDictionaryAndCandidateMatchesTheUpstreamReader() {
        var count = 0
        var entries = 0
        for (file in File(root, ".cache/oracle/data").listFiles().orEmpty().filter { it.extension == "ocd2" }.sorted()) {
            val expected = TextDict.fromBytes(File(root, ".cache/conformance/text/${file.nameWithoutExtension}.txt").readBytes())
            val marisa = MarisaDict.fromBytes(file.readBytes())
            val darts = DartsDict.fromBytes(File(root, ".cache/conformance/ocd/${file.nameWithoutExtension}.ocd").readBytes())
            assertEquals(expected.lexicon.entries.map { it.toString() }, marisa.lexicon.sorted().entries.map { it.toString() }, file.name)
            assertEquals(expected.lexicon.entries.map { it.toString() }, darts.lexicon.sorted().entries.map { it.toString() }, file.name)
            for (entry in expected.lexicon) {
                assertEquals(entry.values, marisa.match(entry.key)?.values, "${file.name}: ${entry.key}")
                assertEquals(entry.values, darts.match(entry.key)?.values, "${file.name}: ${entry.key}")
                assertEquals(entry.key, marisa.matchPrefix(entry.key + "!")?.key, file.name)
                assertEquals(entry.key, darts.matchPrefix(entry.key + "!")?.key, file.name)
                assertEquals(expected.matchAllPrefixes(entry.key).map { it.toString() },
                    marisa.matchAllPrefixes(entry.key).map { it.toString() }, "all prefixes ${file.name}: ${entry.key}")
                entries++
            }
            count++
        }
        assertEquals(22, count)
        println("Native dictionary images verified: $count per format; ordered entries checked: $entries")
    }

    @Test fun binaryReadersRejectTruncatedAndInvalidImages() {
        val bytes = File(root, ".cache/oracle/data/STCharacters.ocd2").readBytes()
        for (size in listOf(0, 10, 18, 40, bytes.size / 2, bytes.size - 1)) {
            assertFailsWith<InvalidFormat>("Truncated ocd2 at $size") { MarisaDict.fromBytes(bytes.copyOf(size)) }
        }
        val ocd = File(root, ".cache/conformance/ocd/STCharacters.ocd").readBytes()
        for (size in listOf(0, 10, 18, 40, ocd.size / 2, ocd.size - 1)) {
            assertFailsWith<InvalidFormat>("Truncated ocd at $size") { DartsDict.fromBytes(ocd.copyOf(size)) }
        }
        val corrupt = bytes.copyOf().apply { this[18] = 0 }
        assertFailsWith<InvalidFormat> { MarisaDict.fromBytes(corrupt) }
    }
}
