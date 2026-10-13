package io.github.opencckmp.tools

import io.github.opencckmp.*
import java.io.File
import kotlin.test.*

/** The writer rebuilds a current image from values rather than retaining native legacy pointers. */
class UpstreamWriterTestLegacyDarts {
    @Test fun legacyLoadedDictionaryReencodesCurrentLayoutAndNativeReaderPreservesEntries() {
        val root = File(System.getProperty("opencc.projectRoot") ?: error("Run upstreamWriterConformance"))
        val source = DartsDict.fromBytes(nativeLegacyDartsImage())
        val image = DictionaryWriters.ocd(source)
        val arrayBytes = (0..3).fold(0L) { value, index -> value or ((image[12 + index].toLong() and 255) shl (index * 8)) }
        assertTrue(arrayBytes > 0 && arrayBytes % 4 == 0L)
        assertNotEquals(0, image[16].toInt() or image[17].toInt() or image[18].toInt() or image[19].toInt())
        val output = DartsDict.fromBytes(image)
        assertEquals(source.lexicon.entries.map { it.toString() }, output.lexicon.entries.map { it.toString() })
        for (entry in source.lexicon) {
            assertEquals(entry.values, output.match(entry.key)?.values)
            assertEquals(entry.defaultValue, output.matchPrefix(entry.key + "!")?.defaultValue)
        }
        val generated = File(root, ".cache/conformance/legacy-kotlin-current.ocd").apply { writeBytes(image) }
        val exported = File(root, ".cache/conformance/legacy-kotlin-current.txt")
        val process = ProcessBuilder(File(root, ".cache/oracle/src/tools/opencc_dict").absolutePath,
            "-i", generated.absolutePath, "-o", exported.absolutePath, "-f", "ocd", "-t", "text")
            .redirectErrorStream(true).start()
        val messages = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), messages)
        assertEquals(source.lexicon.entries.map { it.toString() }, TextDict.fromBytes(exported.readBytes()).lexicon.entries.map { it.toString() })
    }
}
