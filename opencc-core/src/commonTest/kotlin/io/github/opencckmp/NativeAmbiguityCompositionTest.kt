@file:OptIn(ExperimentalOpenCCApi::class)
package io.github.opencckmp

import kotlin.test.*

/** Literal ASCII assertions from pinned ConversionAmbiguitiesTest; offsets are Kotlin UTF16. */
class NativeAmbiguityCompositionTest {
    private fun dict(vararg entries: Pair<String, List<String>>) = TextDict(Lexicon(entries.map { DictEntry(it.first, it.second) }).sorted())
    private fun converter(vararg dicts: Dict, segmentation: Dict? = dicts.firstOrNull()): Converter =
        SingleStageConverter(segmentation?.let { MaxMatchSegmentation(it) }, ConversionChain(dicts.map { Conversion(it) }))
    private fun check(converter: Converter, input: String, output: String, sources: List<String>, spans: List<AmbiguousSpan>) {
        val result = converter.convertWithAmbiguities(input)
        assertEquals(output, result.output); assertEquals(converter.convert(input), result.output)
        assertEquals(sources, result.sources); assertEquals(spans, result.ambiguities)
    }
    @Test fun singleStageMultiValue() {
        check(converter(dict("a" to listOf("x"), "b" to listOf("y", "z"))), "aba", "xyx", listOf("b"), listOf(AmbiguousSpan(1, 1, 0)))
    }
    @Test fun ambiguityInLaterStageMapsToInputSource() {
        check(converter(dict("b" to listOf("y")), dict("y" to listOf("m", "n"))), "ab", "am", listOf("b"), listOf(AmbiguousSpan(1, 1, 0)))
    }
    @Test fun unionPolicyTieBreakMatchesConvert() {
        check(converter(DictGroup(listOf(dict("ab" to listOf("first")), dict("ab" to listOf("second"))), DictGroupMatchPolicy.Union)), "ab", "first", emptyList(), emptyList())
    }
    @Test fun unionNumValuesComesFromWinningChild() {
        val multi = dict("ab" to listOf("x", "y")); val single = dict("ab" to listOf("z"))
        check(converter(DictGroup(listOf(multi, single), DictGroupMatchPolicy.Union)), "ab", "x", listOf("ab"), listOf(AmbiguousSpan(0, 1, 0)))
        check(converter(DictGroup(listOf(single, multi), DictGroupMatchPolicy.Union)), "ab", "z", emptyList(), emptyList())
    }
    @Test fun emptyDictionaryValueDoesNotBreakCompose() {
        val c = converter(dict("a" to listOf("m", "n"), "b" to listOf("")), dict("m" to listOf("M")), segmentation = dict("ab" to listOf("ab")))
        check(c, "ab", "M", listOf("ab"), listOf(AmbiguousSpan(0, 1, 0)))
    }
    @Test fun nulBytePreservedWithoutSegmentation() {
        check(converter(dict("b" to listOf("y", "z")), segmentation = null), "a\u0000b", "a\u0000y", listOf("b"), listOf(AmbiguousSpan(2, 1, 0)))
    }
    @Test fun streamChunkCarriesAnalyzedFlag() {
        val c = converter(dict("b" to listOf("y", "z")))
        assertFalse(AmbiguityStream(PipelineConverter(listOf(c))).finish("b").analyzed)
        assertTrue(AmbiguityStream(c).finish("b").analyzed)
    }
    @Test fun pipelineConverterIsFlaggedUnanalyzed() {
        val c = converter(dict("b" to listOf("y", "z")))
        val result = PipelineConverter(listOf(c)).convertWithAmbiguities("b")
        assertEquals("y", result.output); assertFalse(result.analyzed); assertTrue(result.ambiguities.isEmpty())
        assertTrue(c.convertWithAmbiguities("b").analyzed)
    }
    @Test fun unmatchedTextHasNoAmbiguities() {
        check(converter(dict("b" to listOf("y"))), "hello", "hello", emptyList(), emptyList())
    }
    @Test fun phraseEntryShadowsAmbiguousCharacter() {
        check(converter(dict("b" to listOf("y", "z"), "ab" to listOf("W"))), "abcb", "Wcy", listOf("b"), listOf(AmbiguousSpan(2, 1, 0)))
    }
    @Test fun chunkedStreamMatchesWholeInput() {
        val c = converter(dict("ab" to listOf("XY"), "b" to listOf("y", "z"), "c" to listOf("p", "q"), "de" to listOf("UV", "WW")), dict("XY" to listOf("J"), "p" to listOf("r", "s")))
        val input = "abcabde".repeat(100); val whole = c.convertWithAmbiguities(input); val stream = AmbiguityStream(c)
        val output = StringBuilder(); val sources = mutableListOf<String>(); val spans = mutableListOf<AmbiguousSpan>()
        fun collect(chunk: AmbiguityStream.Chunk) {
            sources.addAll(chunk.newSources); spans.addAll(chunk.ambiguities.map { it.copy(outputOffset = output.length + it.outputOffset) }); output.append(chunk.output)
        }
        input.chunked(7).forEach { collect(stream.convertChunk(it)) }; collect(stream.finish())
        assertEquals(whole.output, output.toString()); assertEquals(whole.sources, sources); assertEquals(whole.ambiguities, spans)
    }
    @Test fun defaultWindowingMatchesConverterStream() {
        val c = converter(dict("ab" to listOf("XY"), "b" to listOf("y", "z")))
        val reference = ConverterStream(c); val stream = AmbiguityStream(c)
        "abcab".repeat(100).chunked(7).forEach { assertEquals(reference.convertChunk(it), stream.convertChunk(it).output) }
        assertEquals(reference.finish(), stream.finish().output)
    }
}
