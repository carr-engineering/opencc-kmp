/* Adapted from OpenCC's unstable candidate/ambiguity APIs, Copyright 2010-2026 Carbo Kuo and contributors. Apache-2.0. */
package io.github.opencckmp

@RequiresOptIn("This mirrors an experimental upstream API and may evolve.", level = RequiresOptIn.Level.WARNING)
annotation class ExperimentalOpenCCApi

/** Discovery-ordered, deduplicated word candidates; an exact match in some chain stage is required. */
@ExperimentalOpenCCApi
fun Converter.getAllConversions(word: String): List<String> {
    UTF8Util.validate(word)
    val converter = unwrapped()
    val chain = converter.conversionChain ?: return emptyList()
    var current = listOf(if (converter is ConfigBasedConverter) converter.normalization.convert(word) else word)
    var matched = false
    for (conversion in chain.conversions) {
        val next = linkedSetOf<String>()
        for (candidate in current) {
            val entry = conversion.dict.match(candidate)
            if (entry == null) next.add(conversion.convert(candidate)) else {
                matched = true
                if (entry.numValues == 0) next.add(candidate) else next.addAll(entry.values)
            }
        }
        current = next.toList()
    }
    return if (matched) current else emptyList()
}

/** Output offsets/lengths count UTF-16 units; sources reference normalized input when applicable. */
@ExperimentalOpenCCApi
data class AmbiguousSpan(val outputOffset: Int, val outputLength: Int, val sourceIndex: Int)

@ExperimentalOpenCCApi
data class AnnotatedConversion(val output: String, val sources: List<String>, val ambiguities: List<AmbiguousSpan>, val analyzed: Boolean = true)

/** Potential ambiguity at any chain stage is retained even when later candidates converge. */
@ExperimentalOpenCCApi
fun Converter.convertWithAmbiguities(text: String): AnnotatedConversion {
    UTF8Util.validate(text)
    val converter = unwrapped()
    val chain = converter.conversionChain ?: return AnnotatedConversion(convert(text), emptyList(), emptyList(), analyzed = false)
    val normalized = if (converter is ConfigBasedConverter) converter.normalization.convert(text) else text
    val sources = mutableListOf<String>()
    val indexes = mutableMapOf<String, Int>()
    val ambiguities = mutableListOf<AmbiguousSpan>()
    val output = StringBuilder(normalized.length)
    for (segment in converter.segmentation?.segment(normalized) ?: Segments(listOf(normalized))) {
        val aligned = walkChain(chain, segment)
        var source = 0
        var target = output.length
        for (run in aligned.runs) {
            if (run.ambiguous) {
                val value = segment.substring(source, source + run.sourceLength)
                val index = indexes.getOrPut(value) { sources.add(value); sources.lastIndex }
                ambiguities.add(AmbiguousSpan(target, run.targetLength, index))
            }
            source += run.sourceLength
            target += run.targetLength
        }
        output.append(aligned.output)
    }
    return AnnotatedConversion(output.toString(), sources, ambiguities)
}

/** Caller-owned streaming state; source indexes are global and newly seen sources are emitted once. */
@ExperimentalOpenCCApi
class AmbiguityStream(val converter: Converter, val maxKeepChars: Int = 16) {
    init { require(maxKeepChars >= 0) }
    data class Chunk(val output: String = "", val ambiguities: List<AmbiguousSpan> = emptyList(),
        val newSources: List<String> = emptyList(), val analyzed: Boolean = true)
    private var pending = ""
    private val sourceIndexes = mutableMapOf<String, Int>()
    val sourceCount: Int get() = sourceIndexes.size
    fun convertChunk(input: String): Chunk {
        pending += input
        val length = flushableTextCount(pending, maxKeepChars)
        if (length == 0) return Chunk()
        val result = convertWindow(pending.substring(0, length))
        pending = pending.substring(length)
        return result
    }
    fun finish(input: String = ""): Chunk {
        pending += input
        if (pending.isEmpty()) return Chunk()
        val result = convertWindow(pending)
        pending = ""
        return result
    }
    private fun convertWindow(text: String): Chunk {
        val converted = converter.convertWithAmbiguities(text)
        val new = mutableListOf<String>()
        val indexes = converted.sources.map { source -> sourceIndexes.getOrPut(source) {
            new.add(source)
            sourceIndexes.size
        } }
        return Chunk(converted.output, converted.ambiguities.map { it.copy(sourceIndex = indexes[it.sourceIndex]) }, new, converted.analyzed)
    }
}
