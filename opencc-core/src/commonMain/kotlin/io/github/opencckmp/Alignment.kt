/* Alignment composition adapted from OpenCC, Copyright 2010-2026 Carbo Kuo and contributors. Apache-2.0. */
package io.github.opencckmp

/** Half-open UTF-16 offsets into the original input and final output. */
data class SegmentMapEntry(val sourceStart: Int, val sourceEnd: Int, val targetStart: Int, val targetEnd: Int)

/** A Kotlin extension beyond upstream. Distinct from the dictionary-stage [Conversion] abstraction. */
data class MappedConversion(val output: String, val map: List<SegmentMapEntry>)

internal data class AlignmentRun(val sourceLength: Int, val targetLength: Int, val ambiguous: Boolean = false)
internal data class AlignedText(val output: String, val runs: List<AlignmentRun>)

internal fun identityRuns(text: String): List<AlignmentRun> {
    val result = mutableListOf<AlignmentRun>()
    var position = 0
    while (position < text.length) {
        val end = UTF8Util.unmatchedEnd(text, position, text.length)
        result.add(AlignmentRun(end - position, end - position))
        position = end
    }
    return result
}

internal fun walkDictionary(dict: Dict, text: String): AlignedText {
    val runs = mutableListOf<AlignmentRun>()
    val output = StringBuilder(text.length)
    var position = 0
    while (position < text.length) {
        val match = dict.matchPrefix(text, position)
        if (match == null) {
            val end = UTF8Util.unmatchedEnd(text, position, text.length)
            output.append(text, position, end)
            runs.add(AlignmentRun(end - position, end - position))
            position = end
        } else {
            output.append(match.defaultValue)
            runs.add(AlignmentRun(match.key.length, match.defaultValue.length, match.numValues > 1))
            position += match.key.length
        }
    }
    return AlignedText(output.toString(), runs)
}

/** Atomic matches widen conservatively whenever two stages' intermediate boundaries straddle. */
internal fun composeAlignment(a: List<AlignmentRun>, b: List<AlignmentRun>): List<AlignmentRun> {
    val output = mutableListOf<AlignmentRun>()
    var left = 0
    var right = 0
    while (left < a.size && right < b.size) {
        var source = a[left].sourceLength
        var middleA = a[left].targetLength
        var ambiguous = a[left++].ambiguous
        var middleB = b[right].sourceLength
        var target = b[right].targetLength
        ambiguous = b[right].ambiguous || ambiguous
        right++
        while (middleA != middleB) {
            if (middleA < middleB) {
                check(left < a.size) { "Alignment intermediate lengths differ" }
                source += a[left].sourceLength
                middleA += a[left].targetLength
                ambiguous = a[left].ambiguous || ambiguous
                left++
            } else {
                check(right < b.size) { "Alignment intermediate lengths differ" }
                middleB += b[right].sourceLength
                target += b[right].targetLength
                ambiguous = b[right].ambiguous || ambiguous
                right++
            }
        }
        output.add(AlignmentRun(source, target, ambiguous))
    }
    while (left < a.size) {
        val run = a[left++]
        check(run.targetLength == 0) { "Alignment intermediate lengths differ" }
        if (output.isEmpty()) output.add(run) else {
            val last = output.last()
            output[output.lastIndex] = last.copy(sourceLength = last.sourceLength + run.sourceLength,
                ambiguous = last.ambiguous || run.ambiguous)
        }
    }
    while (right < b.size) {
        val run = b[right++]
        check(run.sourceLength == 0) { "Alignment intermediate lengths differ" }
        output.add(run)
    }
    return output
}

internal fun walkChain(chain: ConversionChain, text: String): AlignedText {
    var current = text
    var aligned: List<AlignmentRun>? = null
    for (conversion in chain.conversions) {
        val stage = walkDictionary(conversion.dict, current)
        aligned = aligned?.let { composeAlignment(it, stage.runs) } ?: stage.runs
        current = stage.output
    }
    return AlignedText(current, aligned ?: identityRuns(text))
}

internal fun Converter.unwrapped(): Converter = if (this is SimpleConverter) delegate.unwrapped() else this

private fun Converter.aligned(text: String): AlignedText {
    val converter = unwrapped()
    if (converter is PipelineConverter) {
        var current = text
        var runs = identityRuns(text)
        for (child in converter.pipelineConverters) {
            val stage = child.aligned(current)
            runs = composeAlignment(runs, stage.runs)
            current = stage.output
        }
        return AlignedText(current, runs)
    }
    val chain = converter.conversionChain
    if (chain != null) {
        val output = StringBuilder(text.length)
        val runs = mutableListOf<AlignmentRun>()
        for (segment in converter.segmentation?.segment(text) ?: Segments(listOf(text))) {
            val aligned = walkChain(chain, segment)
            output.append(aligned.output)
            runs.addAll(aligned.runs)
        }
        return AlignedText(output.toString(), runs)
    }
    // Host-defined converters without a chain expose one conservative atomic transformation.
    val output = converter.convert(text)
    return AlignedText(output, if (text.isEmpty() && output.isEmpty()) emptyList() else listOf(AlignmentRun(text.length, output.length)))
}

/** Compose original-to-final alignment through normalization and every conversion/pipeline stage. */
fun Converter.convertWithMap(text: String): MappedConversion {
    UTF8Util.validate(text)
    val aligned = aligned(text)
    var source = 0
    var target = 0
    val map = aligned.runs.map { run ->
        SegmentMapEntry(source, source + run.sourceLength, target, target + run.targetLength).also {
            source += run.sourceLength
            target += run.targetLength
        }
    }
    check(source == text.length && target == aligned.output.length) { "Incomplete conversion map" }
    return MappedConversion(aligned.output, map)
}
