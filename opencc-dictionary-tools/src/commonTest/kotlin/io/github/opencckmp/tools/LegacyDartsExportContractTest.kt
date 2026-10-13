package io.github.opencckmp.tools

import io.github.opencckmp.*
import kotlin.test.*

class LegacyDartsExportContractTest {
    @Test fun legacyReaderToWriterRebuildsFixedWidthCurrentImage() {
        val legacy = DartsDict.fromBytes(nativeLegacyDartsImage())
        val image = DictionaryWriters.ocd(legacy)
        assertEquals("OPENCCDARTS1", image.copyOfRange(0, 12).decodeToString())
        fun word(offset: Int) = (0..3).fold(0L) { value, index -> value or ((image[offset + index].toLong() and 255) shl (index * 8)) }
        val arrayBytes = word(12).toInt()
        assertTrue(arrayBytes > 0 && arrayBytes % 4 == 0)
        // Current root begins after a four-byte size, not the legacy eight-byte size.
        assertNotEquals(0L, word(16))
        val payloadOffset = 16 + arrayBytes
        assertEquals(legacy.lexicon.size.toLong(), word(payloadOffset))
        val fixedPayloadSize = 3 * 4 + legacy.lexicon.entries.sumOf { entry ->
            UTF8Util.encode(entry.key).size + 1 + 2 * 4 + entry.numValues * 4 +
                entry.values.sumOf { UTF8Util.encode(it).size + 1 }
        }
        assertEquals(payloadOffset + fixedPayloadSize, image.size)
        val current = DartsDict.fromBytes(image)
        assertEquals(legacy.lexicon.entries.map { it.toString() }, current.lexicon.entries.map { it.toString() })
        for (entry in legacy.lexicon) {
            assertEquals(entry.values, current.match(entry.key)?.values)
            assertEquals(entry.defaultValue, current.matchPrefix(entry.key + "!")?.defaultValue)
        }
        assertEquals(Conversion(legacy).convert("汉字ab😀"), Conversion(current).convert("汉字ab😀"))
    }
}
