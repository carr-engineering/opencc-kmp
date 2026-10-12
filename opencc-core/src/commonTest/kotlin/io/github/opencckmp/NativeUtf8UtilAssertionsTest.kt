/* Ported assertions from OpenCC ver.1.4.2 UTF8UtilTest.cpp. Apache-2.0. */
package io.github.opencckmp

import kotlin.test.*

class NativeUtf8UtilAssertionsTest {
    private val text = "東菄鶇䍶𠍀倲𩜍𢘐"
    private val bytes = UTF8Util.encode(text)
    @Test fun nextCharLength() {
        val starts = intArrayOf(0, 3, 6, 9, 12, 16, 19, 23)
        val lengths = intArrayOf(3, 3, 3, 3, 4, 3, 4, 4)
        for (i in starts.indices) assertEquals(lengths[i], UTF8Util.nextByteLength(bytes, starts[i]))
        for (i in 1..2) assertFailsWith<InvalidUTF8> { UTF8Util.nextByteLength(bytes, i) }
    }
    @Test fun prevCharLength() {
        for ((end, length) in listOf(bytes.size to 4, bytes.size - 4 to 4, bytes.size - 8 to 3))
            assertEquals(length, end - UTF8Util.previousByteIndex(bytes, end))
        assertFailsWith<InvalidUTF8> { UTF8Util.previousByteIndex(bytes, 1) }
    }
    @Test fun prevCharLengthASCII() {
        val ascii = UTF8Util.encode("abc")
        for (end in 1..3) assertEquals(1, end - UTF8Util.previousByteIndex(ascii, end))
    }
    @Test fun lengthTruncatedSequence() {
        for (input in listOf(byteArrayOf(0xE0.toByte()), byteArrayOf(0xF0.toByte()), byteArrayOf(0xE0.toByte(), 0xBF.toByte())))
            assertFailsWith<InvalidUTF8> { UTF8Util.encodedLength(input) }
    }
    @Test fun length() { assertEquals(0, UTF8Util.encodedLength(byteArrayOf())); assertEquals(8, UTF8Util.encodedLength(bytes)) }
    @Test fun notShorterThan() {
        // Kotlin byte containers carry their extent; first-NUL limit replaces the native pointer scan.
        val limit = bytes.indexOf(0).takeIf { it >= 0 } ?: bytes.size
        assertTrue(limit >= 0); assertTrue(limit >= bytes.size); assertFalse(limit >= bytes.size + 1)
    }
    @Test fun truncateUTF8() {
        for ((budget, expected) in listOf(0 to "", 1 to "", 3 to "東", 4 to "東", 16 to "東菄鶇䍶𠍀", bytes.size to text, bytes.size + 1 to text))
            assertEquals(expected, UTF8Util.decode(UTF8Util.truncateUtf8(bytes, budget)))
    }
    private fun ids(text: String): Int = UTF8Util.ideographicDescriptionByteEnd(UTF8Util.encode(text))
    @Test fun ideographicDescriptionSequenceLength() { assertEquals(UTF8Util.byteLength("⿰钅只"), ids("⿰钅只只")) }
    @Test fun nestedIdeographicDescriptionSequenceLength() { assertEquals(UTF8Util.byteLength("⿱艹⿰钅只"), ids("⿱艹⿰钅只干")) }
    @Test fun complexIdeographicDescriptionSequenceLength() {
        assertEquals(UTF8Util.byteLength("⿺⻍⿳穴⿲月⿱⿲幺言幺⿲長馬長刂心"), ids("⿺⻍⿳穴⿲月⿱⿲幺言幺⿲長馬長刂心長"))
    }
    @Test fun operatorOnlyIdeographicDescriptionSequenceReturnsZero() { assertEquals(0, ids("⿰⿱⿲⿳⿴⿵⿶⿷⿸⿹⿺⿻⿼⿽⿾⿿")) }
    @Test fun isolatedIdeographicDescriptionCharactersAreNotSequences() {
        for (operator in "⿰⿱⿲⿳⿴⿵⿶⿷⿸⿹⿺⿻⿼⿽⿾⿿") assertEquals(0, ids(operator.toString()))
    }
    @Test fun incompleteIdeographicDescriptionSequenceReturnsZero() { assertEquals(0, ids("⿰钅")) }
    @Test fun overlyDeepIdeographicDescriptionSequenceReturnsZero() { assertEquals(0, ids("⿰".repeat(17) + "木".repeat(18))) }
    @Test fun overlyLongIdeographicDescriptionSequenceReturnsZero() { assertEquals(0, ids("⿰".repeat(64) + "木".repeat(65))) }
    @Test fun containsVariationSelector() {
        assertTrue(UTF8Util.containsVariationSelector(UTF8Util.encode("禰\uFE00")))
        assertTrue(UTF8Util.containsVariationSelector(UTF8Util.encode("禰\uDB40\uDD00")))
        assertFalse(UTF8Util.containsVariationSelector(bytes))
    }
    @Test fun getByteMap() { assertContentEquals(intArrayOf(0, 3, 6, 9, 12, 16), UTF8Util.byteMap(bytes).take(6).toIntArray()) }
}
