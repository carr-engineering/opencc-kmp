@file:OptIn(ExperimentalOpenCCApi::class)
package io.github.opencckmp

import kotlin.test.*

class AlignmentTest {
    private fun dict(vararg entries: Pair<String, List<String>>) = TextDict(Lexicon(entries.map { DictEntry(it.first, it.second) }).sorted())
    private fun converter(vararg dicts: Dict, segmentation: Dict? = null): Converter =
        SingleStageConverter(segmentation?.let { MaxMatchSegmentation(it) }, ConversionChain(dicts.map { Conversion(it) }))
    private fun verifyMap(converter: Converter, text: String): MappedConversion {
        val result = converter.convertWithMap(text)
        assertEquals(converter.convert(text), result.output)
        var source = 0
        var target = 0
        for (entry in result.map) {
            assertEquals(source, entry.sourceStart); assertEquals(target, entry.targetStart)
            UTF8Util.requireRange(text, entry.sourceStart, entry.sourceEnd)
            UTF8Util.requireRange(result.output, entry.targetStart, entry.targetEnd)
            source = entry.sourceEnd; target = entry.targetEnd
        }
        assertEquals(text.length, source); assertEquals(result.output.length, target)
        return result
    }

    @Test fun mapsComposeNormalizationAndEveryStage() {
        val normalization = converter(dict("神" to listOf("神")))
        val main = converter(dict("神" to listOf("神明")), dict("明" to listOf("明亮")))
        val config = ConfigBasedConverter(normalization, main)
        val result = verifyMap(SimpleConverter.fromConfig(config), "a神𠀀")
        assertEquals("a神明亮𠀀", result.output)
        assertEquals(listOf(SegmentMapEntry(0, 1, 0, 1), SegmentMapEntry(1, 2, 1, 4), SegmentMapEntry(2, 4, 4, 6)), result.map)
    }

    @Test fun straddlingAndDeletionKeepCompleteSourceCoverage() {
        val first = converter(dict("a" to listOf("x"), "b" to listOf("y")))
        val second = converter(dict("xy" to listOf("Q")))
        val pipeline = PipelineConverter(listOf(first, second))
        assertEquals(listOf(SegmentMapEntry(0, 2, 0, 1)), verifyMap(pipeline, "ab").map)
        val deletion = converter(dict("a" to listOf(""), "b" to listOf("")), dict())
        assertEquals(listOf(SegmentMapEntry(0, 2, 0, 0)), verifyMap(deletion, "ab").map)
        val trailing = converter(dict("x" to listOf("")), dict())
        verifyMap(trailing, "ax")
        verifyMap(trailing, "xa")
        assertTrue(verifyMap(pipeline, "").map.isEmpty())
    }

    @Test fun segmentationAndIdsRemainAtomicInMaps() {
        val source = converter(dict("ab" to listOf("X"), "龙" to listOf("龍")), segmentation = dict("a" to listOf("a")))
        assertEquals("ab⿰龙马", verifyMap(source, "ab⿰龙马").output)
        assertEquals(SegmentMapEntry(2, 5, 2, 5), source.convertWithMap("ab⿰龙马").map.last())
        val last = MaxMatchSegmentation(dict("a" to listOf("a")))
        assertSame(last, PipelineConverter(listOf(source, SingleStageConverter(last, ConversionChain(emptyList())))).segmentation)
    }

    @Test fun wordCandidatesBranchDeduplicateNormalizeAndCarryPartialMatches() {
        val source = converter(dict("里" to listOf("里", "裏")), dict("裏" to listOf("裡")))
        assertEquals(listOf("里", "裡"), source.getAllConversions("里"))
        assertTrue(source.getAllConversions("里面").isEmpty())
        assertTrue(source.getAllConversions("国").isEmpty())
        val partial = converter(dict("里面" to listOf("裏面")), dict("裏" to listOf("裡")))
        assertEquals(listOf("裡面"), partial.getAllConversions("里面"))
        val collapsed = converter(dict("面" to listOf("麵", "面")), dict("麵" to listOf("面")))
        assertEquals(listOf("面"), collapsed.getAllConversions("面"))
        val normalized = ConfigBasedConverter(converter(dict("里" to listOf("里"))), source)
        assertEquals(listOf("里", "裡"), SimpleConverter.fromConfig(normalized).getAllConversions("里"))
        assertTrue(PipelineConverter(listOf(source)).getAllConversions("里").isEmpty())
        val keyOnly = converter(dict("通道" to emptyList()))
        assertEquals(listOf("通道"), keyOnly.getAllConversions("通道"))
        assertEquals("通道", keyOnly.convert("通道"))
        val ids = converter(dict("里字" to listOf("⿱艹里")), dict("里" to listOf("裏")))
        assertEquals(listOf("⿱艹里"), ids.getAllConversions("里字"))
    }

    @Test fun ambiguitiesRetainPrecisionSourcesAndUtf16Units() {
        val source = converter(dict("zz" to listOf("zz")), dict("x" to listOf("m", "n")))
        val annotated = source.convertWithAmbiguities("aaaxbbb😀x")
        assertEquals(source.convert("aaaxbbb😀x"), annotated.output)
        assertEquals(listOf("x"), annotated.sources)
        assertEquals(listOf(AmbiguousSpan(3, 1, 0), AmbiguousSpan(9, 1, 0)), annotated.ambiguities)
        val expanded = converter(dict("b" to listOf("😀", "𠀀")))
        assertEquals(listOf(AmbiguousSpan(0, 2, 0)), expanded.convertWithAmbiguities("b").ambiguities)
    }

    @Test fun straddledAmbiguityAndDeletionSurviveLaterStages() {
        val source = converter(dict("a" to listOf("x"), "b" to listOf("y", "z")), dict("xy" to listOf("Q")))
        val annotated = source.convertWithAmbiguities("ab")
        assertEquals("Q", annotated.output)
        assertEquals(listOf("ab"), annotated.sources)
        assertEquals(listOf(AmbiguousSpan(0, 1, 0)), annotated.ambiguities)
        val deleted = converter(dict("x" to listOf("", "a")), dict())
        val empty = deleted.convertWithAmbiguities("x")
        assertEquals("", empty.output)
        assertEquals(listOf("x"), empty.sources)
        assertEquals(listOf(AmbiguousSpan(0, 0, 0)), empty.ambiguities)
        val unsupported = PipelineConverter(listOf(source)).convertWithAmbiguities("ab")
        assertFalse(unsupported.analyzed)
        assertEquals("Q", unsupported.output)
    }

    @Test fun ambiguityGroupsAndNormalizedSourcesMatchConversion() {
        val group = DictGroup(listOf(dict("a" to listOf("x")), dict("ab" to listOf("y", "z"))), DictGroupMatchPolicy.Union)
        val source = converter(group, segmentation = group)
        assertEquals(source.convert("ab"), source.convertWithAmbiguities("ab").output)
        assertEquals(listOf("ab"), source.convertWithAmbiguities("ab").sources)
        val normalized = ConfigBasedConverter(converter(dict("里" to listOf("里"))), converter(dict("里" to listOf("里", "裏"))))
        assertEquals(listOf("里"), SimpleConverter.fromConfig(normalized).convertWithAmbiguities("里").sources)
    }

    @Test fun ambiguityStreamDeduplicatesAcrossChunksAndHoldsSurrogatePairs() {
        val source = converter(dict("b" to listOf("y", "z"), "😀" to listOf("X", "Y")))
        val stream = AmbiguityStream(source, maxKeepChars = 0)
        val first = stream.convertChunk("b" + charArrayOf(0xD83D.toChar()).concatToString())
        assertEquals("y", first.output)
        assertEquals(listOf("b"), first.newSources)
        val second = stream.convertChunk(charArrayOf(0xDE00.toChar()).concatToString() + "b")
        assertEquals("Xy", second.output)
        assertEquals(listOf("😀"), second.newSources)
        assertEquals(listOf(1, 0), second.ambiguities.map { it.sourceIndex })
        assertEquals(2, stream.sourceCount)
        assertEquals("", stream.finish().output)
        val pending = AmbiguityStream(source)
        assertEquals("", pending.convertChunk("b").output)
        assertEquals("y", pending.finish().output)
        assertEquals(1, pending.sourceCount)
    }
}
