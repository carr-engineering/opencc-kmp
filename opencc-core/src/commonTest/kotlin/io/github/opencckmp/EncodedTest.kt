package io.github.opencckmp

import kotlin.test.*

class EncodedTest {
    private fun dictionary() = TextDict.fromText("汉\t漢\n字\tZ\nabc\tABC\n𠀀\t甲\n")
    private fun engine(segmented: Boolean = false): Converter {
        val dict = dictionary()
        return SingleStageConverter(if (segmented) MaxMatchSegmentation(dict) else null, ConversionChain(listOf(Conversion(dict))))
    }
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun partialAndNonCanonicalSequencesPreserveOriginalBytes() {
        for (hex in listOf("e2", "e280", "c080", "eda080", "f4908080", "e26162", "f880808080", "fc8080808080")) {
            val raw = bytes(hex)
            assertContentEquals(raw, engine().convertUtf8(raw))
            assertContentEquals(raw, engine(true).convertUtf8(raw))
            assertFailsWith<InvalidUTF8> { UTF8Util.decode(raw) }
        }
        assertFailsWith<InvalidUTF8> { engine().convertUtf8(bytes("80")) }
        val empty = SingleStageConverter(null, ConversionChain(emptyList()))
        assertContentEquals(bytes("80"), empty.convertUtf8(bytes("80")))
        assertContentEquals(bytes("e6"), engine().convertUtf8Prefix("汉".encodeToByteArray(), 1))
    }
    @Test fun byteRangesModesAndBufferUnits() {
        val input = "!汉\u0000abc字?".encodeToByteArray()
        assertEquals("漢\u0000ABCZ", UTF8Util.decode(engine().convertUtf8(input, 1, input.size - 1)))
        assertEquals("漢ABCZ", UTF8Util.decode(engine(true).convertUtf8(input, 1, input.size - 1)))
        assertEquals("漢", UTF8Util.decode(engine().convertUtf8(input, 1, input.size - 1, EncodedInputMode.NullTerminated)))
        val output = ByteArray(16) { '!'.code.toByte() }
        assertEquals(3, engine().convertUtf8To(input, output, 2, 1, input.size - 1))
        assertEquals("漢", UTF8Util.decode(output, 2, 5))
        assertEquals('!'.code.toByte(), output[5])
        val before = output.copyOf()
        assertFailsWith<IllegalArgumentException> { engine().convertUtf8To(input, output, 14, 1, input.size - 1) }
        assertContentEquals(before, output)
        assertFailsWith<IllegalArgumentException> { engine().convertUtf8(input, -1, input.size) }
        assertFailsWith<IllegalArgumentException> { engine().convertUtf8(input, 1, input.size + 1) }
        assertEquals(8, engine().convertUtf8To(input, output, 0, 1, input.size - 1, EncodedInputMode.LengthDelimited))
        assertEquals("漢\u0000ABCZ", UTF8Util.decode(output, 0, 8))
    }
    @Test fun encodedInspectionMatchesNativeTraceNulSemantics() {
        val input = "汉\u0000abc字".encodeToByteArray()
        val inspected = engine().inspectUtf8(input)
        assertContentEquals(input, inspected.input)
        assertEquals("漢", UTF8Util.decode(inspected.output))
        assertEquals(listOf("汉"), inspected.segments.map { UTF8Util.decode(it) })
        assertEquals(listOf("漢"), inspected.stages[0].segments.map { UTF8Util.decode(it) })
        assertEquals("漢ABCZ", UTF8Util.decode(engine(true).inspectUtf8(input).output))
        assertEquals("漢\u0000ABCZ", UTF8Util.decode(engine().convertUtf8(input)))
        assertEquals("漢\u0000ABCZ", engine().convert("汉\u0000abc字"))
        assertEquals("漢ABCZ", engine(true).convert("汉\u0000abc字"))
        assertEquals("漢", engine().inspect("汉\u0000abc字").output)
        val mapped = engine(true).convertWithMap("pre\u0000abc\u0000post字")
        assertEquals("preABCZ", mapped.output)
        assertEquals("pre\u0000abc\u0000post字".length, mapped.map.last().sourceEnd)
        assertTrue(mapped.map.any { it.sourceEnd > it.sourceStart && it.targetStart == it.targetEnd })
    }
    @Test fun inlineDictionaryNulAcceptanceUsesUpstreamLogicalPrefixes() {
        val converter = Config().newFromString("""{"name":"nul","conversion_chain":[{"dict":{"type":"inline","entries":{"abc\u0000tail":"value\u0000ignored","a":"\u0000deletion"}}}]}""")
        assertEquals("value", converter.convert("abc"))
        assertEquals("", converter.convert("a"))
        assertEquals("value", UTF8Util.decode(converter.convertUtf8("abc".encodeToByteArray())))
        val duplicate = """{"conversion_chain":[{"dict":{"type":"inline","entries":{"a":"A","a\u0000tail":"B"}}}]}"""
        assertFailsWith<InvalidFormat> { Config().newFromString(duplicate) }
    }
    @Test fun encodedIdsUseNativeMaskedCodePointsAndRemainAtomic() {
        val canonical = "⿰汉字".encodeToByteArray()
        assertContentEquals(canonical, engine().convertUtf8(canonical))
        val masked = bytes("e27fb0") + "汉字".encodeToByteArray()
        assertContentEquals(masked, engine().convertUtf8(masked))
        assertEquals("⿰漢", UTF8Util.decode(engine().convertUtf8("⿰汉".encodeToByteArray())))
    }
    @Test fun directEncodedDictionaryLookupsRetainEntryIdentityAndOrder() {
        val dict = TextDict.fromText("a\tA\nab\tB C\n𠀀\t甲\n")
        assertSame(dict.match("ab"), dict.matchPrefixUtf8("abc".encodeToByteArray()))
        assertEquals(listOf("ab", "a"), dict.matchAllPrefixesUtf8("abc".encodeToByteArray()).map { it.key })
        assertSame(dict.match("𠀀"), dict.matchPrefixUtf8("𠀀!".encodeToByteArray()))
        assertEquals(4, dict.match("𠀀")!!.keyByteLength)
        assertEquals(4, UTF8Util.byteLength("𠀀"))
        assertNull(dict.matchPrefixUtf8(bytes("f090")))
        assertEquals("ab", dict.matchPrefixUtf8("ab".encodeToByteArray() + bytes("80"))?.key)
    }
    @Test fun encodedStreamingRetainsSplitCharactersAndNativeErrors() {
        val stream = Utf8ConverterStream(engine(), 0)
        val input = "汉".encodeToByteArray()
        assertContentEquals(byteArrayOf(), stream.convertChunk(input.copyOfRange(0, 1)))
        assertContentEquals(byteArrayOf(), stream.convertChunk(input.copyOfRange(1, 2)))
        assertEquals("漢", UTF8Util.decode(stream.convertChunk(input.copyOfRange(2, 3))))
        assertContentEquals(byteArrayOf(), stream.finish())
        assertContentEquals(byteArrayOf(), stream.convertChunk(bytes("e2")))
        assertContentEquals(bytes("e2"), stream.finish())
        assertFailsWith<InvalidUTF8> { Utf8ConverterStream(engine()).convertChunk(bytes("e26162")) }
        val ids = Utf8ConverterStream(engine(), 0)
        assertContentEquals(byteArrayOf(), ids.convertChunk("⿰汉".encodeToByteArray()))
        assertContentEquals("⿰汉字".encodeToByteArray(), ids.convertChunk("字".encodeToByteArray()))
    }
    @Test fun facadeEncodedErrorsAndEmptyStreamFinishUseOwnedState() {
        val provider = MemoryResourceProvider(mapOf("c.json" to """{"conversion_chain":[{"dict":{"type":"inline","entries":{"汉":"漢"}}}]}""".encodeToByteArray()))
        val context = OpenCC(Config(provider))
        val handle = assertNotNull(context.open("c"))
        assertNull(handle.convertUtf8(bytes("80")))
        assertIs<InvalidUTF8>(context.lastError)
        assertContentEquals(bytes("e6"), handle.convertUtf8("汉".encodeToByteArray(), 0, 1))
        assertIs<InvalidUTF8>(context.lastError) // successful calls do not erase prior errors
        assertEquals("漢", UTF8Util.decode(assertNotNull(handle.convertUtf8("汉\u0000x".encodeToByteArray(), mode = EncodedInputMode.NullTerminated))))
        handle.close()
        assertNull(handle.convertUtf8(byteArrayOf()))
        var calls = 0
        val converter = object : Converter {
            override fun convert(text: String): String { calls++; return text }
            override fun inspect(text: String) = ConversionInspectionResult(text, text)
        }
        assertEquals("", ConverterStream(converter).finish())
        assertContentEquals(byteArrayOf(), Utf8ConverterStream(converter).finish())
        assertEquals(0, calls)
    }
}
