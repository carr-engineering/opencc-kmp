package io.github.opencckmp

import kotlin.test.*

class EngineTest {
    private fun dict(vararg entries: Pair<String, String>): TextDict =
        TextDict(Lexicon(entries.map { DictEntry(it.first, listOf(it.second)) }))
    private fun converter(dict: Dict, segmentation: Dict? = null): Converter =
        SingleStageConverter(segmentation?.let { MaxMatchSegmentation(it) }, ConversionChain(listOf(Conversion(dict))))

    @Test fun longestPrefixAndCandidateOrder() {
        val dict = TextDict.fromText("# metadata\n\uD840\uDC00\t甲 乙\na\tA\nab\tB C\n")
        assertEquals("ab", dict.matchPrefix("abc")?.key)
        assertEquals(listOf("B", "C"), dict.match("ab")?.values)
        assertEquals(listOf("ab", "a"), dict.matchAllPrefixes("abc").map { it.key })
        assertEquals("甲Bc", converter(dict).convert("\uD840\uDC00abc"))
        assertEquals("bc", converter(dict).convert("bc"))
    }

    @Test fun groupPoliciesAndTies() {
        val early = dict("a" to "first", "z" to "early")
        val later = dict("ab" to "long", "z" to "later")
        assertEquals("firstb", converter(DictGroup(listOf(early, later))).convert("ab"))
        assertEquals("long", converter(UnionDictGroup(listOf(early, later))).convert("ab"))
        assertEquals("early", converter(UnionDictGroup(listOf(early, later))).convert("z"))
        assertEquals(listOf("ab", "a"), DictGroup(listOf(early, later)).matchAllPrefixes("ab").map { it.key })
    }

    @Test fun segmentationBoundariesArePreservedAcrossConversion() {
        val conversion = dict("bc" to "X")
        assertEquals("aX", converter(conversion).convert("abc"))
        assertEquals("abc", converter(conversion, dict("ab" to "ab")).convert("abc"))
        assertEquals(listOf("!", "ab", "?c"), MaxMatchSegmentation(dict("ab" to "ab")).segment("!ab?c").values)
    }

    @Test fun ideographicDescriptionsRemainAtomicWhenUnmatched() {
        val engine = converter(dict("龙" to "龍", "马" to "馬"))
        assertEquals("⿰龙马 龍馬", engine.convert("⿰龙马 龙马"))
        assertEquals("⿲龙马木", engine.convert("⿲龙马木"))
        assertEquals("⿰龍", engine.convert("⿰龙"))
    }

    @Test fun encodingRangesAndBufferCapacity() {
        val engine = converter(dict("汉" to "漢", "字" to "字"))
        assertEquals("漢", engine.convertPrefix("汉字", 1))
        assertEquals("字", engine.convert("汉字", 1, 2))
        assertFailsWith<IllegalArgumentException> { engine.convert("\uD840\uDC00", 0, 1) }
        assertFailsWith<InvalidUTF16> { engine.convert("\uD800") }
        assertFailsWith<InvalidUTF8> { engine.convertUtf8(byteArrayOf(0xFF.toByte())) }
        assertEquals("漢", UTF8Util.decode(engine.convertUtf8("汉字".encodeToByteArray(), 0, 3)))
        val output = charArrayOf('!', '!', '!')
        assertEquals(1, engine.convertTo("汉", output, 1))
        assertContentEquals(charArrayOf('!', '漢', '!'), output)
        assertFailsWith<IllegalArgumentException> { engine.convertTo("汉字", output, 2) }
        assertContentEquals(charArrayOf('!', '漢', '!'), output)
    }

    @Test fun configNormalizationInlineOptionsAndInspection() {
        val json = """
            { // accepted by upstream
              "name": "test",
              "normalization": [{"dict":{"type":"inline","entries":{"神":"神"}}}],
              "segmentation": {"type":"mmseg","dict":{"type":"inline","entries":{"神":"神"}}},
              "conversion_chain": [
                {"dict":{"type":"inline","entries":{"神":"神明 神祇"}}},
                {"dict":{"type":"group","match_policy":"union","may_output_tofu":true,
                  "dicts":[{"type":"inline","entries":{"明":"明亮"}}]}},
              ],
            }
        """.trimIndent()
        val loader = Config()
        val engine = loader.newFromString(json)
        assertEquals("神明亮", engine.convert("神"))
        assertEquals("神明", loader.newFromString(json, options = ConfigLoadOptions(false)).convert("神"))
        val inspected = engine.inspect("神")
        assertEquals(engine.convert("神"), inspected.output)
        assertEquals(2, inspected.pipelineStages.size)
        assertEquals(listOf("神"), inspected.pipelineStages[1].segments)
        assertEquals(2, inspected.pipelineStages[1].stages.size)
    }

    @Test fun missingResourcesRelativeNamesAndMalformedConfig() {
        val resources = MemoryResourceProvider(mapOf(
            "data/test.json" to """{"name":"test","conversion_chain":[{"dict":{"type":"text","file":"dict.txt"}}]}""".encodeToByteArray(),
            "data/dict.txt" to "汉\t漢\n".encodeToByteArray(),
        ))
        assertEquals("漢", Config(resources).newFromFile("data/test.json").convert("汉"))
        assertFailsWith<FileNotFound> { Config(resources).newFromFile("missing.json") }
        assertFailsWith<InvalidFormat> { Config().newFromString("{\"conversion_chain\": []} garbage") }
        assertFailsWith<InvalidFormat> { Config().newFromString("""{"conversion_chain":[{"dict":{"type":"inline","entries":{"a":"A","a":"B"}}}]}""") }
        assertFailsWith<InvalidFormat> { TextDict.fromText("a\tA\na\tB\n") }
        assertFailsWith<InvalidTextDictionary> { TextDict.fromText("missing delimiter") }
    }

    @Test fun streamingFinalChunkAndSupplementaryBoundaries() {
        val engine = converter(dict("汉字" to "漢字", "\uD840\uDC00" to "甲"))
        val stream = ConverterStream(engine, 2)
        assertEquals("", stream.convertChunk("汉"))
        assertEquals("", stream.convertChunk("字"))
        assertEquals("漢字甲", stream.finish("\uD840\uDC00"))
        val split = ConverterStream(engine, 0)
        assertEquals("", split.convertChunk("\uD840"))
        assertEquals("甲", split.convertChunk("\uDC00"))
        assertEquals("", split.finish())
    }

    @Test fun ownedDictionaryValuesAndResourceSnapshots() {
        val candidates = mutableListOf("A", "B")
        val entry = DictEntry("a", candidates)
        candidates[0] = "corrupt"
        assertEquals("A", entry.defaultValue)
        val bytes = byteArrayOf(1, 2)
        val provider = MemoryResourceProvider(mapOf("data" to bytes))
        bytes[0] = 0
        val copy = provider.read("data")
        copy[1] = 0
        assertContentEquals(byteArrayOf(1, 2), provider.read("data"))
    }

    @Test fun facadesOwnTheirErrorsAndHandleLifetime() {
        val resources = MemoryResourceProvider(mapOf("s2t.json" to
            """{"name":"test","conversion_chain":[{"dict":{"type":"inline","entries":{"汉":"漢"}}}]}""".encodeToByteArray()))
        assertEquals("漢", SimpleConverter("s2t", resources).convert("汉"))
        val context = OpenCC(Config(resources))
        val first = assertNotNull(context.open())
        val second = assertNotNull(context.open())
        assertEquals("漢", first.convert("汉"))
        assertTrue(first.close())
        assertNull(first.convert("汉"))
        assertTrue(assertNotNull(context.error()).contains("closed"))
        assertEquals("漢", second.convert("汉"))
        val independent = OpenCC(Config(resources))
        assertNull(independent.error())
        assertNull(independent.open("missing"))
        assertIs<FileNotFound>(independent.lastError)
    }
}
