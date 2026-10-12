/* Adapted from OpenCC's unstable byte-oriented ambiguity walker/stream. Apache-2.0. */
@file:OptIn(ExperimentalOpenCCApi::class)
package io.github.opencckmp

/** Encoded offsets and lengths count bytes. Arrays in results belong to the caller. */
@ExperimentalOpenCCApi
class EncodedAmbiguousSpan(val outputOffset: Int, val outputLength: Int, val sourceIndex: Int)
@ExperimentalOpenCCApi
class EncodedAnnotatedConversion(val output: ByteArray, val sources: List<ByteArray>,
                                 val ambiguities: List<EncodedAmbiguousSpan>, val analyzed: Boolean = true)

private class AlignedBytes(val output: ByteArray, val runs: List<AlignmentRun>)
private class SourceBytes(bytes: ByteArray) {
    val value = bytes.copyOf()
    private val hash = value.contentHashCode()
    override fun hashCode(): Int = hash
    override fun equals(other: Any?): Boolean = other is SourceBytes && value.contentEquals(other.value)
}
private fun walkBytes(dict: Dict, input: ByteArray): AlignedBytes {
    val output = ByteOutput(input.size)
    val runs = mutableListOf<AlignmentRun>()
    var cursor = 0
    while (cursor < input.size) {
        val matched = dict.matchPrefixUtf8(input, cursor)
        if (matched == null) {
            val end = EncodedUTF8.unmatchedEnd(input, cursor, input.size)
            output.append(input, cursor, end)
            runs.add(AlignmentRun(end - cursor, end - cursor))
            cursor = end
        } else {
            val keyLength = minOf(matched.keyByteLength, input.size - cursor)
            val valueLength = UTF8Util.byteLength(matched.defaultValue)
            output.append(matched.defaultValue)
            runs.add(AlignmentRun(keyLength, valueLength, matched.numValues > 1))
            cursor += keyLength
        }
    }
    return AlignedBytes(output.result(), runs)
}

@ExperimentalOpenCCApi
fun Converter.convertWithAmbiguitiesUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size,
                                        mode: EncodedInputMode = EncodedInputMode.LengthDelimited): EncodedAnnotatedConversion {
    val end = EncodedUTF8.end(input, startIndex, endIndex, mode)
    val converter = unwrapped()
    val chain = converter.conversionChain ?: return EncodedAnnotatedConversion(convertUtf8(input, startIndex, end), emptyList(), emptyList(), false)
    val original = input.copyOfRange(startIndex, end)
    val normalized = if (converter is ConfigBasedConverter) converter.normalization.convertUtf8(original) else original
    val sources = mutableListOf<ByteArray>()
    val indexes = mutableMapOf<SourceBytes, Int>()
    val spans = mutableListOf<EncodedAmbiguousSpan>()
    val output = ByteOutput(normalized.size)
    var targetOffset = 0
    fun segment(bytes: ByteArray) {
        var current = bytes
        var aligned: List<AlignmentRun>? = null
        for (conversion in chain.conversions) {
            val stage = walkBytes(conversion.dict, current)
            aligned = aligned?.let { composeAlignment(it, stage.runs) } ?: stage.runs
            current = stage.output
        }
        val runs = aligned ?: listOf(AlignmentRun(bytes.size, bytes.size))
        var source = 0
        var target = targetOffset
        for (run in runs) {
            if (run.ambiguous) {
                val key = SourceBytes(bytes.copyOfRange(source, source + run.sourceLength))
                val index = indexes.getOrPut(key) { sources.add(key.value.copyOf()); sources.lastIndex }
                spans.add(EncodedAmbiguousSpan(target, run.targetLength, index))
            }
            source += run.sourceLength
            target += run.targetLength
        }
        output.append(current)
        targetOffset += current.size
    }
    val segmentation = converter.segmentation
    if (segmentation == null) segment(normalized) else for (bytes in segmentation.segmentUtf8(normalized)) {
        segment(bytes.copyOfRange(0, EncodedUTF8.end(bytes, 0, bytes.size, EncodedInputMode.NullTerminated)))
    }
    return EncodedAnnotatedConversion(output.result(), sources, spans)
}

/** Owned encoded pending buffer and global source table, with native byte/IDS window boundaries. */
@ExperimentalOpenCCApi
class Utf8AmbiguityStream(val converter: Converter, val maxKeepChars: Int = 16) {
    init { require(maxKeepChars >= 0) }
    class Chunk(val output: ByteArray = byteArrayOf(), val ambiguities: List<EncodedAmbiguousSpan> = emptyList(),
                val newSources: List<ByteArray> = emptyList(), val analyzed: Boolean = true)
    private var pending = byteArrayOf()
    private val indexes = mutableMapOf<SourceBytes, Int>()
    val sourceCount: Int get() = indexes.size
    fun convertChunk(input: ByteArray): Chunk {
        pending += input
        val count = EncodedUTF8.flushable(pending, maxKeepChars)
        if (count == 0) return Chunk()
        val chunk = convertWindow(pending.copyOfRange(0, count))
        pending = pending.copyOfRange(count, pending.size)
        return chunk
    }
    fun finish(input: ByteArray = byteArrayOf()): Chunk {
        pending += input
        if (pending.isEmpty()) return Chunk()
        val chunk = convertWindow(pending)
        pending = byteArrayOf()
        return chunk
    }
    private fun convertWindow(input: ByteArray): Chunk {
        val result = converter.convertWithAmbiguitiesUtf8(input)
        val new = mutableListOf<ByteArray>()
        val global = result.sources.map { value ->
            val key = SourceBytes(value)
            indexes.getOrPut(key) { new.add(key.value.copyOf()); indexes.size }
        }
        return Chunk(result.output, result.ambiguities.map { EncodedAmbiguousSpan(it.outputOffset, it.outputLength, global[it.sourceIndex]) }, new, result.analyzed)
    }
}
