package io.github.opencckmp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Utf8UtilitiesTest {
    @Test fun nativeUtilityGoldenCases() {
        for (row in nativeUtf8UtilityCases) {
            val fields = row.split('|')
            val input = fields[2].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            fun operation(): String = when (fields[0]) {
                "width" -> UTF8Util.nextByteLengthOrZero(input, 0).toString()
                "length" -> UTF8Util.encodedLength(input).toString()
                "truncate" -> UTF8Util.truncateUtf8(input, fields[1].toInt()).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
                "selector" -> if (UTF8Util.containsVariationSelector(input)) "1" else "0"
                "ids" -> UTF8Util.ideographicDescriptionByteEnd(input).toString()
                "incomplete" -> if (UTF8Util.isIncompleteIdeographicDescriptionBytePrefix(input)) "1" else "0"
                else -> error(row)
            }
            if (fields[3] == "ERROR") assertFailsWith<InvalidUTF8>(row) { operation() }
            else assertEquals(fields[3], operation(), row)
        }
    }

    @Test fun scalarBoundariesAndByteBudgets() {
        val text = "A𠍀東"
        assertEquals(1, UTF8Util.previousIndex(text, 3))
        assertEquals(3, UTF8Util.previousIndex(text, 4))
        assertFailsWith<InvalidUTF16> { UTF8Util.previousIndex(text, 2) }
        assertContentEquals(intArrayOf(0, 1, 5, 8), UTF8Util.byteMap(text))
        assertEquals("A", UTF8Util.truncate(text, 4))
        assertEquals("A𠍀", UTF8Util.truncate(text, 5))
        assertEquals(3, UTF8Util.commonPrefixEnd(text, "A𠍀西"))
        assertEquals(4, UTF8Util.commonPrefixEnd(text, text))
        assertEquals(-1, UTF8Util.reverseCompare("A𠍀", "B𠍀"))
        assertEquals(0, UTF8Util.reverseCompare(text, text))
    }

    @Test fun encodedTraversalMatchesLeadingByteContract() {
        for (lead in 0..255) {
            val expected = when {
                lead < 128 -> 1
                lead in 192..223 -> 2
                lead in 224..239 -> 3
                lead in 240..247 -> 4
                lead in 248..251 -> 5
                lead in 252..253 -> 6
                else -> 0
            }
            assertEquals(expected, UTF8Util.nextByteLengthOrZero(byteArrayOf(lead.toByte()), 0))
        }
        val legacy = byteArrayOf(0xF8.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 65, 0, 66)
        assertEquals(2, UTF8Util.encodedLength(legacy))
        assertEquals(4, UTF8Util.encodedLength(legacy, mode = EncodedInputMode.LengthDelimited))
        assertEquals(5, UTF8Util.nextByteIndex(legacy, 0))
        assertEquals(0, UTF8Util.previousByteIndex(legacy, 5))
        assertContentEquals(intArrayOf(0, 5, 6), UTF8Util.byteMap(legacy))
        assertContentEquals(legacy.copyOfRange(0, 5), UTF8Util.truncateUtf8(legacy, 5))
        assertContentEquals(byteArrayOf(), UTF8Util.truncateUtf8(legacy, 4))
        assertFailsWith<InvalidUTF8> { UTF8Util.encodedLength(byteArrayOf(0xE0.toByte(), 1)) }
        assertFailsWith<InvalidUTF8> { UTF8Util.previousByteIndex(byteArrayOf(0x80.toByte()), 1) }
        assertEquals(4, UTF8Util.findNextInline(UTF8Util.encode("東A\nB"), 'B'.code.toByte()))
    }

    @Test fun bomIdsAndVariationSelectors() {
        assertEquals(3, UTF8Util.skipBom(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 65)))
        assertEquals(0, UTF8Util.skipBom(byteArrayOf(0xEF.toByte(), 0xBB.toByte())))
        val ids = "⿰東𠍀"
        assertEquals(4, UTF8Util.ideographicDescriptionEnd(ids))
        assertTrue(UTF8Util.isIncompleteIdeographicDescriptionPrefix("⿰東"))
        assertFalse(UTF8Util.isIncompleteIdeographicDescriptionPrefix("東"))
        val bytes = UTF8Util.encode(ids)
        assertEquals(bytes.size, UTF8Util.ideographicDescriptionByteEnd(bytes))
        assertTrue(UTF8Util.isIncompleteIdeographicDescriptionBytePrefix(bytes, endIndex = bytes.size - 1))
        assertEquals(0, UTF8Util.ideographicDescriptionEnd("⿰".repeat(16) + "東".repeat(17)))
        assertTrue(UTF8Util.containsVariationSelector("東\uFE0F"))
        assertTrue(UTF8Util.containsVariationSelector("東\uDB40\uDD00"))
        assertFalse(UTF8Util.isVariationSelector(0xE01F0))
        assertTrue(UTF8Util.containsVariationSelector(byteArrayOf(0x80.toByte()) + UTF8Util.encode("\uFE0F")))
        assertFalse(UTF8Util.containsVariationSelector(byteArrayOf(0xEF.toByte(), 0xB8.toByte())))
    }
}
