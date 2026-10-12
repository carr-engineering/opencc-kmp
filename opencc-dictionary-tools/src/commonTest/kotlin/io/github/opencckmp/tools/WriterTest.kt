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
    @Test fun marisaNestedTrieAndEndiannessRoundTrip() {
        val source = TextDict.fromText("a\tA B\nab\tX\nabc\tY Z\nlong_shared_prefix_one_suffix\tone\nlong_shared_prefix_two_suffix\ttwo\n汉\t漢\n汉字\t漢字\n𠀀\t甲 乙\n😀\tsmile\n")
        for (order in DictionaryByteOrder.entries) for (wordSize in listOf(32, 64)) {
            val encoded = DictionaryWriters.ocd2(source, order, wordSize)
            val decoded = MarisaDict.fromBytes(encoded)
            assertEquals(decoded.lexicon.entries.map { it.toString() }, MarisaDict.fromBytes(encoded, order, wordSize).lexicon.entries.map { it.toString() })
            assertEquals(source.lexicon.entries.associate { it.key to it.values }, decoded.lexicon.entries.associate { it.key to it.values })
            assertEquals(listOf("abc", "ab", "a"), decoded.matchAllPrefixes("abcd").map { it.key })
        }
        assertEquals(0, MarisaDict.fromBytes(DictionaryWriters.ocd2(TextDict(Lexicon(emptyList())))).lexicon.size)
    }
    @Test fun invalidKeysAndOversizeValueLengthsAreRejected() {
        assertFailsWith<InvalidFormat> { DictionaryWriters.ocd(TextDict(Lexicon(listOf(DictEntry("a\u0000b", listOf("x")))))) }
        assertFailsWith<InvalidFormat> { DictionaryWriters.serializedValues(Lexicon(listOf(DictEntry("key", listOf("x".repeat(65535)))))) }
    }
    @Test fun allFormatRoutesAndCppJiebaMetadata() {
        val formats = listOf(DictionaryFormat.Text, DictionaryFormat.Ocd, DictionaryFormat.Ocd2)
        val expected = dictionary.lexicon.entries.associate { it.key to it.values }
        for (from in formats) for (to in formats) {
            val input = DictionaryConverter.write(dictionary, from)
            val output = DictionaryConverter.convert(input, from, to)
            assertEquals(expected, DictionaryConverter.load(listOf(output), to).lexicon.entries.associate { it.key to it.values }, "$from/$to")
        }
        val merged = DictionaryConverter.load(listOf(
            "汉字 100 n\n软件 20 n\n".encodeToByteArray(), "汉字 200 nz\n自定义\n默认词 nz\n".encodeToByteArray()), DictionaryFormat.CppJiebaUtf8)
        assertEquals(listOf("200", "nz", "user_freq"), merged.match("汉字")?.values)
        assertEquals(listOf("", "", "user_default"), merged.match("自定义")?.values)
        assertEquals(listOf("", "nz", "user_default"), merged.match("默认词")?.values)
        assertEquals(listOf("20", "n", "base"), merged.match("软件")?.values)
        assertEquals("A", DictionaryConverter.load(listOf("a A\n".encodeToByteArray()), DictionaryFormat.TextSpace).match("a")?.defaultValue)
        assertFailsWith<InvalidFormat> { DictionaryConverter.load(emptyList(), DictionaryFormat.CppJiebaUtf8) }
    }
}
