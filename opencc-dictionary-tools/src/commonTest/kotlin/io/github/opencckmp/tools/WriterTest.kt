package io.github.opencckmp.tools

import io.github.opencckmp.*
import kotlin.test.*

class WriterTest {
    private val dictionary = TextDict.fromText("a\tA B\nab\tX\n汉\t漢\n汉字\t漢字\n𠀀\t甲 乙\n")
    @Test fun dartsRoundTripPreservesKeysCandidatesAndPrefixMatching() {
        for (order in DictionaryByteOrder.entries) {
            val decoded = DartsDict.fromBytes(DictionaryWriters.ocd(dictionary, order))
            assertEquals(dictionary.lexicon.entries.map { it.toString() }, decoded.lexicon.entries.map { it.toString() })
            assertEquals("ab", decoded.matchPrefix("abc")?.key)
            assertEquals(listOf("甲", "乙"), decoded.match("𠀀")?.values)
        }
    }
    @Test fun valuesRoundTripAndEmptyDictionary() {
        val lexicon = Lexicon(listOf(DictEntry("", listOf("first", "second")), DictEntry("", listOf(""))))
        for (order in DictionaryByteOrder.entries) {
            val decoded = SerializedValues.fromBytes(DictionaryWriters.serializedValues(lexicon, order), order)
            assertEquals(lexicon.entries.map { it.values }, decoded.values)
        }
        assertEquals(0, DartsDict.fromBytes(DictionaryWriters.ocd(TextDict(Lexicon(emptyList())))).lexicon.size)
    }
    @Test fun invalidKeysAndOversizeValueLengthsAreRejected() {
        assertFailsWith<InvalidFormat> { DictionaryWriters.ocd(TextDict(Lexicon(listOf(DictEntry("a\u0000b", listOf("x")))))) }
        assertFailsWith<InvalidFormat> { DictionaryWriters.serializedValues(Lexicon(listOf(DictEntry("key", listOf("x".repeat(65535)))))) }
    }
}
