/* Ported assertions from OpenCC ver.1.4.2 LexiconAnnotationTest and TextDictTest. Apache-2.0. */
package io.github.opencckmp.tools

import io.github.opencckmp.*
import kotlin.test.*

class UpstreamLexiconSerializationTest {
    private fun size(text: String): Int = TextDict.fromText(text).lexicon.size
    @Test fun parseCommentLines() { assertEquals(2, size("# This is a header comment\n# Line 2 of header\n\nA\tB\nC\tD\n")) }
    @Test fun parseAttachedComment() { assertEquals(2, size("# Header\n\n# Comment for A\nA\tB\nC\tD\n")) }
    @Test fun parseFloatingComment() { assertEquals(2, size("A\tB\n\n# This is a floating comment\n\nC\tD\n")) }
    @Test fun parseFooterComment() { assertEquals(2, size("A\tB\nC\tD\n\n# Footer comment\n# Line 2 of footer\n")) }
    @Test fun serializeIgnoresComments() {
        val dict = TextDict.fromText("# Header\n\n# Comment for B\nB\tBB\nA\tAA\n\n# Footer\n")
        assertEquals(listOf("A\tAA", "B\tBB"), UTF8Util.decode(DictionaryWriters.text(dict)).trimEnd('\n').lines())
    }
    @Test fun sortIgnoresComments() {
        assertEquals(3, size("# Header\n\n# Comment for C\nC\tCC\n# Comment for A\nA\tAA\nB\tBB\n"))
    }
    @Test fun defaultBehaviorIgnoresComments() { assertEquals(2, size("A\tB\nC\tD\n")) }
    @Test fun defaultBehaviorAcceptsCommentLines() { assertEquals(1, size("# This is a comment\nA\tB\n")) }

    private val source = "BYVoid\tbyv\nzigzagzig\tzag\n積羽沉舟\t羣輕折軸\n清\tTsing\n清華\tTsinghua\n清華大學\tTsinghuaUniversity\n"
    @Test fun textDictSerialization() {
        val dict = TextDict.fromText(source)
        val encoded = DictionaryWriters.text(dict)
        assertEquals(6, UTF8Util.decode(encoded).trimEnd('\n').lines().size)
        assertTrue(UTF8Util.decode(encoded).contains("清華\tTsinghua\n"))
    }
    @Test fun textDictDeserialization() {
        // Native FILE* save/load maps to tooling-owned byte output/input.
        val dict = DictionaryConverter.load(listOf(DictionaryWriters.text(TextDict.fromText(source))), DictionaryFormat.Text)
        assertEquals("BYVoid", dict.match("BYVoid")?.key); assertEquals("byv", dict.match("BYVoid")?.defaultValue)
        assertNull(dict.match("")); assertNull(dict.match("xxx"))
        assertEquals("清華大學", dict.matchPrefix("清華大學")?.key)
        assertEquals("TsinghuaUniversity", dict.matchPrefix("清華大學")?.defaultValue)
        assertEquals("BYVoid", dict.matchPrefix("BYVoid123")?.key); assertEquals("byv", dict.matchPrefix("BYVoid123")?.defaultValue)
        assertEquals("積羽沉舟", dict.matchPrefix("積羽沉舟")?.key); assertEquals("羣輕折軸", dict.matchPrefix("積羽沉舟")?.defaultValue)
        assertNull(dict.matchPrefix("Unknown")); assertNull(dict.matchPrefix(""))
        val matches = dict.matchAllPrefixes("清華大學計算機系")
        assertEquals(listOf("清華大學", "清華", "清"), matches.map { it.key })
        assertEquals(listOf("TsinghuaUniversity", "Tsinghua", "Tsing"), matches.map { it.defaultValue })
    }
}
