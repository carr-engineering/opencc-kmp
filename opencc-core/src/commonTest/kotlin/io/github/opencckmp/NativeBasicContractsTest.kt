/* Ported assertions from OpenCC ver.1.4.2 Conversion[Chain], DictGroup and TextDict tests. Apache-2.0. */
package io.github.opencckmp

import kotlin.test.*

class NativeBasicContractsTest {
    private fun dict(vararg entries: Pair<String, String>): TextDict =
        TextDict(Lexicon(entries.map { DictEntry(it.first, listOf(it.second)) }).sorted())
    private fun phrases(): TextDict = dict("太后" to "太后", "头发" to "頭髮", "干燥" to "乾燥", "鼠标" to "鼠標")
    private fun characters(): TextDict = TextDict(Lexicon(listOf(
        DictEntry("后", listOf("后", "後")), DictEntry("发", listOf("發", "髮")),
        DictEntry("干", listOf("幹", "乾", "干")), DictEntry("里", listOf("裏", "里")),
    )).sorted())
    private fun group(): DictGroup = DictGroup(listOf(phrases(), characters()))
    private fun engine(dict: Dict): SingleStageConverter =
        SingleStageConverter(MaxMatchSegmentation(dict), ConversionChain(listOf(Conversion(dict))))
    private val input = "太后的头发干燥"
    private val expected = "太后的頭髮乾燥"

    @Test fun conversionConvertString() { assertEquals(expected, Conversion(group()).convert(input)) }
    @Test fun conversionConvertCString() {
        assertEquals(expected, UTF8Util.decode(Conversion(group()).convertUtf8(UTF8Util.encode(input), mode = EncodedInputMode.NullTerminated)))
    }
    @Test fun conversionConvertMixedAsciiAndChinese() {
        val prefix = "The quick brown fox jumps over the lazy dog 0123456789 "
        val suffix = " <html attr=\"value\">tail</html>"
        assertEquals(prefix + expected + "、「」" + expected + suffix,
            Conversion(group()).convert(prefix + input + "、「」" + input + suffix))
    }
    @Test fun conversionPreserveDictGroupPriority() {
        assertEquals("Xbc", Conversion(DictGroup(listOf(dict("a" to "X"), dict("ab" to "Y")))).convert("abc"))
    }
    @Test fun conversionPreserveNestedDictGroupPriority() {
        assertEquals("Xbc", Conversion(DictGroup(listOf(DictGroup(listOf(dict("a" to "X"), dict("ab" to "Y"))), dict("abc" to "Z")))).convert("abc"))
    }
    @Test fun conversionPreserveIdeographicDescriptionSequenceComponents() {
        assertEquals("⿰钅只隻", Conversion(dict("钅" to "釒", "只" to "隻")).convert("⿰钅只只"))
    }
    @Test fun conversionPreserveComplexIdeographicDescriptionSequenceComponents() {
        assertEquals("⿺⻍⿳穴⿲月⿱⿲幺言幺⿲長馬長刂心长",
            Conversion(dict("長" to "长", "馬" to "马", "心" to "芯")).convert("⿺⻍⿳穴⿲月⿱⿲幺言幺⿲長馬長刂心長"))
    }
    @Test fun conversionDoesNotPreserveIsolatedIdeographicDescriptionCharacters() {
        val conversion = Conversion(dict("表" to "錶", "证" to "證"))
        assertEquals("錶意文字描述字元包括這些字元：⿰⿱⿲⿳⿴⿵⿶⿷⿸⿹⿺⿻⿼⿽⿾⿿",
            conversion.convert("表意文字描述字元包括這些字元：⿰⿱⿲⿳⿴⿵⿶⿷⿸⿹⿺⿻⿼⿽⿾⿿"))
        assertEquals("⿾证證", conversion.convert("⿾证证"))
    }
    @Test fun conversionTruncatedUtf8Sequence() {
        val truncated = byteArrayOf(0xE5.toByte(), 0xB9.toByte())
        assertContentEquals(UTF8Util.encode("幹") + truncated, Conversion(group()).convertUtf8(UTF8Util.encode("干") + truncated))
    }

    @Test fun groupKeyMaxLength() {
        val group = group()
        // Native lengths 6/6/3 count UTF8 bytes; Kotlin ordinary text uses UTF16 units 2/2/1.
        assertEquals(6, group.lexicon.entries.maxOf { it.keyByteLength })
        assertEquals(2, group.keyMaxLength)
        assertEquals(2, group.groupItems.first().keyMaxLength)
        assertEquals(1, group.groupItems.last().keyMaxLength)
    }
    @Test fun groupMatchPolicyDefaultsToShortCircuit() { assertEquals(DictGroupMatchPolicy.ShortCircuit, group().matchPolicy) }
    @Test fun groupExplicitMatchPolicy() { assertEquals(DictGroupMatchPolicy.ShortCircuit, DictGroup(listOf(phrases(), characters()), DictGroupMatchPolicy.ShortCircuit).matchPolicy) }
    @Test fun groupUnionMatchPolicy() { assertEquals(DictGroupMatchPolicy.Union, UnionDictGroup(listOf(phrases(), characters())).matchPolicy) }
    @Test fun groupUnionMatchPrefixPrefersLaterLongerMatch() {
        val entry = UnionDictGroup(listOf(dict("意大利" to "義大利"), dict("意大利面" to "義大利麵"))).matchPrefix("意大利面")
        assertNotNull(entry); assertEquals("意大利面", entry.key); assertEquals("義大利麵", entry.defaultValue)
    }
    @Test fun groupSimpleGroupTest() {
        val group = group()
        assertNull(group.matchPrefix("Unknown"))
        val entry = assertNotNull(group.matchPrefix("里面"))
        assertEquals(1, entry.keyLength); assertEquals(3, entry.keyByteLength); assertEquals("裏", entry.defaultValue)
        assertEquals(listOf("乾燥", "幹"), group.matchAllPrefixes("干燥").map { it.defaultValue })
    }
    @Test fun groupTaiwanPhraseGroupTest() {
        val group = DictGroup(listOf(phrases(), dict("鼠标" to "滑鼠", "服务器" to "伺服器", "克罗地亚" to "克羅埃西亞")))
        assertEquals("鼠標", group.matchPrefix("鼠标")?.defaultValue)
        assertEquals("克羅埃西亞", group.matchPrefix("克罗地亚")?.defaultValue)
        assertEquals(listOf("鼠標"), group.matchAllPrefixes("鼠标").map { it.defaultValue })
    }

    @Test fun chainConvert() {
        val chain = ConversionChain(listOf(Conversion(group()), Conversion(dict("裏" to "裡"))))
        assertEquals(listOf("裡面"), chain.convert(Segments(listOf("里面"))).values)
    }
    @Test fun chainStreamKeepsIncompleteIdeographicDescriptionSequence() {
        val stream = ConverterStream(engine(dict("钅" to "釒", "只" to "隻")), 1)
        assertEquals("prefix⿰钅只隻", stream.convertChunk("prefix⿰钅") + stream.finish("只只"))
    }
    @Test fun chainStreamKeepsIncompleteIdeographicDescriptionSequenceBeforeKeepLimit() {
        val stream = ConverterStream(engine(dict("只" to "隻")), 16)
        val prefix = "prefix" + "⿰".repeat(10) + "木".repeat(10)
        assertEquals(prefix + "只隻", stream.convertChunk(prefix) + stream.finish("只只"))
    }
    private fun stages(): List<Converter> = listOf(engine(dict("钅" to "釒")), engine(dict("釒" to "金")))
    @Test fun pipelineConvertChainsStagesInOrder() { assertEquals("金", PipelineConverter(stages()).convert("钅")) }
    @Test fun pipelineConvertEmptyPipelineIsIdentity() { assertEquals("钅", PipelineConverter(emptyList()).convert("钅")) }
    @Test fun pipelineGetSegmentationReturnsLastStage() {
        val stages = stages(); assertSame(stages.last().segmentation, PipelineConverter(stages).segmentation)
    }
    @Test fun pipelineGetSegmentationEmptyPipelineReturnsNullptr() { assertNull(PipelineConverter(emptyList()).segmentation) }
    @Test fun pipelineInspectPopulatesPipelineStages() {
        val result = PipelineConverter(stages()).inspect("钅")
        assertEquals("钅", result.input); assertEquals("金", result.output)
        assertTrue(result.segments.isEmpty()); assertTrue(result.stages.isEmpty()); assertEquals(2, result.pipelineStages.size)
        val first = result.pipelineStages[0]; val second = result.pipelineStages[1]
        assertEquals("钅", first.input); assertEquals("釒", first.output)
        assertTrue(first.segments.isNotEmpty()); assertTrue(first.stages.isNotEmpty()); assertTrue(first.pipelineStages.isEmpty())
        assertEquals(first.output, second.input); assertEquals("金", second.output)
        assertTrue(second.stages.isNotEmpty()); assertTrue(second.pipelineStages.isEmpty())
    }
    @Test fun pipelineInspectEmptyPipelineReturnsTrivialResult() {
        val result = PipelineConverter(emptyList()).inspect("钅")
        assertEquals("钅", result.input); assertEquals("钅", result.output); assertTrue(result.pipelineStages.isEmpty())
    }
    @Test fun pipelineInspectOutputMatchesConvert() {
        val pipeline = PipelineConverter(stages()); assertEquals(pipeline.convert("钅"), pipeline.inspect("钅").output)
    }

    private fun textDict(): TextDict = dict("BYVoid" to "byv", "zigzagzig" to "zag", "積羽沉舟" to "羣輕折軸",
        "清" to "Tsing", "清華" to "Tsinghua", "清華大學" to "TsinghuaUniversity")
    @Test fun textDictDictTest() {
        val dict = textDict()
        for (query in listOf("BYVoid", "BYVoid123")) {
            val entry = if (query == "BYVoid") dict.match(query) else dict.matchPrefix(query)
            assertEquals("BYVoid", entry?.key); assertEquals("byv", entry?.defaultValue)
        }
        assertNull(dict.match("")); assertNull(dict.match("xxx"))
        assertEquals("清華大學", dict.matchPrefix("清華大學")?.key)
        assertEquals("TsinghuaUniversity", dict.matchPrefix("清華大學")?.defaultValue)
        assertEquals("積羽沉舟", dict.matchPrefix("積羽沉舟")?.key); assertEquals("羣輕折軸", dict.matchPrefix("積羽沉舟")?.defaultValue)
        assertNull(dict.matchPrefix("Unknown")); assertNull(dict.matchPrefix(""))
        val matches = dict.matchAllPrefixes("清華大學計算機系")
        assertEquals(listOf("清華大學", "清華", "清"), matches.map { it.key })
        assertEquals(listOf("TsinghuaUniversity", "Tsinghua", "Tsing"), matches.map { it.defaultValue })
    }
    @Test fun textDictDeserializationEmptyLine() {
        val dict = TextDict.fromText("A\tB\n\n\nC\tD E\n\n")
        assertEquals(2, dict.lexicon.size); assertEquals("A", assertNotNull(dict.match("A")).key)
    }
    @Test fun textDictExactMatch() {
        val dict = textDict(); val entry = assertNotNull(dict.match("積羽沉舟"))
        assertEquals(1, entry.numValues); assertEquals("羣輕折軸", entry.defaultValue)
        assertNull(dict.match("積羽沉舟衆口鑠金"))
    }
    @Test fun textDictMatchPrefix() {
        val dict = textDict(); val bytes = UTF8Util.encode("清華")
        for (end in listOf(3, 5)) assertEquals("Tsing", dict.matchPrefixUtf8(bytes, endIndex = end)?.defaultValue)
        assertEquals("Tsinghua", dict.matchPrefixUtf8(bytes)?.defaultValue)
        // Native's oversized pointer extent is replaced by the complete bounded Kotlin input.
        assertEquals("Tsinghua", dict.matchPrefix("清華")?.defaultValue)
    }
}
