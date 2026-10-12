/* Adapted from OpenCC. Copyright 2010-2026 Carbo Kuo and contributors. Apache-2.0. */
package io.github.opencckmp

interface Segmentation {
    fun segment(text: String): Segments
    fun segmentUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size): List<ByteArray> =
        segment(UTF8Util.decode(input, startIndex, endIndex)).map { UTF8Util.encode(it) }
}

class Segments(values: List<String>) : Iterable<String> {
    private val stored = values.toList()
    val values: List<String> get() = stored.toList()
    val size: Int get() = stored.size
    operator fun get(index: Int): String = stored[index]
    override fun iterator(): Iterator<String> = readOnlyIterator(stored)
    override fun toString(): String = stored.joinToString("")
}

class MaxMatchSegmentation(val dict: Dict) : Segmentation {
    override fun segment(text: String): Segments {
        UTF8Util.validate(text)
        val result = mutableListOf<String>()
        var cursor = 0
        var unmatchedStart = 0
        while (cursor < text.length) {
            val matched = dict.matchPrefix(text, cursor)
            if (matched == null) {
                cursor = UTF8Util.unmatchedEnd(text, cursor, text.length)
            } else {
                if (cursor > unmatchedStart) result.add(text.substring(unmatchedStart, cursor))
                result.add(matched.key)
                cursor += matched.key.length
                unmatchedStart = cursor
            }
        }
        if (cursor > unmatchedStart) result.add(text.substring(unmatchedStart, cursor))
        return Segments(result)
    }
    override fun segmentUtf8(input: ByteArray, startIndex: Int, endIndex: Int): List<ByteArray> {
        EncodedUTF8.range(input, startIndex, endIndex)
        val result = mutableListOf<ByteArray>()
        var cursor = startIndex
        var unmatchedStart = cursor
        while (cursor < endIndex) {
            val matched = dict.matchPrefixUtf8(input, cursor, endIndex)
            if (matched == null) cursor = EncodedUTF8.unmatchedEnd(input, cursor, endIndex) else {
                if (cursor > unmatchedStart) result.add(input.copyOfRange(unmatchedStart, cursor))
                result.add(input.copyOfRange(cursor, cursor + matched.keyByteLength))
                cursor += matched.keyByteLength
                unmatchedStart = cursor
            }
        }
        if (cursor > unmatchedStart) result.add(input.copyOfRange(unmatchedStart, cursor))
        return result
    }
}

/** One dictionary stage. Distinct from full-text Converter and segment-map result types. */
class Conversion(val dict: Dict) {
    fun convert(text: String): String = buildString(text.length) { appendConverted(text, this) }
    fun appendConverted(text: String, output: Appendable) {
        var cursor = 0
        var unmatchedStart = 0
        while (cursor < text.length) {
            val matched = dict.matchPrefix(text, cursor)
            if (matched == null) {
                cursor = UTF8Util.unmatchedEnd(text, cursor, text.length)
            } else {
                if (cursor > unmatchedStart) output.append(text, unmatchedStart, cursor)
                output.append(matched.defaultValue)
                cursor += matched.key.length
                unmatchedStart = cursor
            }
        }
        if (cursor > unmatchedStart) output.append(text, unmatchedStart, cursor)
    }
    fun convert(segments: Segments): Segments = Segments(segments.map { convert(it.substringBefore('\u0000')) })
    fun convertUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size,
                    mode: EncodedInputMode = EncodedInputMode.LengthDelimited): ByteArray {
        val end = EncodedUTF8.end(input, startIndex, endIndex, mode)
        val output = ByteOutput(end - startIndex)
        var cursor = startIndex
        var unmatchedStart = cursor
        while (cursor < end) {
            val matched = dict.matchPrefixUtf8(input, cursor, end)
            if (matched == null) cursor = EncodedUTF8.unmatchedEnd(input, cursor, end) else {
                output.append(input, unmatchedStart, cursor)
                output.append(matched.defaultValue)
                cursor += matched.keyByteLength
                unmatchedStart = cursor
            }
        }
        output.append(input, unmatchedStart, cursor)
        return output.result()
    }
}

class ConversionChain(conversions: List<Conversion>) {
    private val stored = conversions.toList()
    val conversions: List<Conversion> get() = stored.toList()
    fun convert(text: String): String {
        var current = text
        for (conversion in stored) current = conversion.convert(current)
        return current
    }
    fun convert(input: Segments): Segments {
        var current = input
        for (conversion in stored) current = conversion.convert(current)
        return current
    }
    fun convertWithTrace(input: Segments): List<Segments> {
        var current = input
        return stored.map { conversion -> conversion.convert(current).also { current = it } }
    }
    fun convertUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size,
                    mode: EncodedInputMode = EncodedInputMode.LengthDelimited): ByteArray {
        var current = input.copyOfRange(startIndex, EncodedUTF8.end(input, startIndex, endIndex, mode))
        for (conversion in stored) current = conversion.convertUtf8(current)
        return current
    }
}

data class ConversionInspectionStage(val index: Int, val segments: List<String>)
data class ConversionInspectionResult(
    val input: String,
    val output: String,
    val segments: List<String> = emptyList(),
    val stages: List<ConversionInspectionStage> = emptyList(),
    val pipelineStages: List<ConversionInspectionResult> = emptyList(),
)

interface Converter {
    val segmentation: Segmentation? get() = null
    val conversionChain: ConversionChain? get() = null
    fun convert(text: String): String
    fun inspect(text: String): ConversionInspectionResult

    /** Offsets are half-open UTF-16 ranges and may not split supplementary characters. */
    fun convert(text: String, startIndex: Int, endIndex: Int): String {
        UTF8Util.requireRange(text, startIndex, endIndex)
        return convert(text.substring(startIndex, endIndex))
    }
    fun convertPrefix(text: String, length: Int): String = convert(text, 0, length)
    fun convertTo(text: String, output: Appendable): Int {
        val converted = convert(text)
        output.append(converted)
        return converted.length
    }
    fun convertTo(text: String, output: CharArray, offset: Int = 0): Int {
        require(offset in 0..output.size) { "Invalid output offset" }
        val converted = convert(text)
        require(converted.length <= output.size - offset) { "Insufficient output capacity" }
        converted.toCharArray(output, offset)
        return converted.length
    }
    /** Standard converters preserve upstream encoded traversal; custom converters may override it. */
    fun convertUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size,
                    mode: EncodedInputMode = EncodedInputMode.LengthDelimited): ByteArray =
        UTF8Util.encode(convert(UTF8Util.decode(input, startIndex, EncodedUTF8.end(input, startIndex, endIndex, mode))))
    fun convertUtf8Prefix(input: ByteArray, length: Int, mode: EncodedInputMode = EncodedInputMode.LengthDelimited): ByteArray =
        convertUtf8(input, 0, length, mode)
    /** Buffer overload defaults to upstream's NUL-terminated prefix input; capacity is always safe. */
    fun convertUtf8To(input: ByteArray, output: ByteArray, offset: Int = 0,
                      startIndex: Int = 0, endIndex: Int = input.size,
                      mode: EncodedInputMode = EncodedInputMode.NullTerminated): Int {
        require(offset in 0..output.size) { "Invalid output offset" }
        val converted = convertUtf8(input, startIndex, endIndex, mode)
        require(converted.size <= output.size - offset) { "Insufficient output capacity" }
        converted.copyInto(output, offset)
        return converted.size
    }
    fun inspectUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size,
                    mode: EncodedInputMode = EncodedInputMode.LengthDelimited): EncodedInspectionResult =
        inspect(UTF8Util.decode(input, startIndex, EncodedUTF8.end(input, startIndex, endIndex, mode))).toEncoded()
}

class SingleStageConverter(
    override val segmentation: Segmentation?,
    override val conversionChain: ConversionChain,
) : Converter {
    override fun convert(text: String): String {
        UTF8Util.validate(text)
        if (segmentation == null) return conversionChain.convert(text)
        return buildString(text.length) {
            for (segment in segmentation.segment(text)) append(conversionChain.convert(segment.substringBefore('\u0000')))
        }
    }
    override fun inspect(text: String): ConversionInspectionResult {
        UTF8Util.validate(text)
        val initial = segmentation?.segment(text) ?: Segments(listOf(text))
        val trace = conversionChain.convertWithTrace(initial)
        fun reported(segments: Segments) = segments.values.map { it.substringBefore('\u0000') }
        return ConversionInspectionResult(text, reported(trace.lastOrNull() ?: initial).joinToString(""),
            reported(initial), trace.mapIndexed { index, segments -> ConversionInspectionStage(index + 1, reported(segments)) })
    }
    override fun convertUtf8(input: ByteArray, startIndex: Int, endIndex: Int, mode: EncodedInputMode): ByteArray {
        val end = EncodedUTF8.end(input, startIndex, endIndex, mode)
        val segmentation = segmentation ?: return conversionChain.convertUtf8(input, startIndex, end)
        val output = ByteOutput(end - startIndex)
        for (segment in segmentation.segmentUtf8(input, startIndex, end)) {
            output.append(conversionChain.convertUtf8(segment, mode = EncodedInputMode.NullTerminated))
        }
        return output.result()
    }
    override fun inspectUtf8(input: ByteArray, startIndex: Int, endIndex: Int, mode: EncodedInputMode): EncodedInspectionResult {
        val end = EncodedUTF8.end(input, startIndex, endIndex, mode)
        val snapshot = input.copyOfRange(startIndex, end)
        val initial = segmentation?.segmentUtf8(snapshot) ?: listOf(snapshot)
        var current = initial
        val trace = conversionChain.conversions.mapIndexed { index, conversion ->
            current = current.map { conversion.convertUtf8(it, mode = EncodedInputMode.NullTerminated) }
            EncodedInspectionStage(index + 1, current.nulTerminatedSnapshots())
        }
        return EncodedInspectionResult(snapshot, joinEncoded(current.nulTerminatedSnapshots()),
            initial.nulTerminatedSnapshots(), trace)
    }
}

open class PipelineConverter(converters: List<Converter>) : Converter {
    protected val children = converters.toList()
    internal val pipelineConverters: List<Converter> get() = children
    override val segmentation: Segmentation? get() = children.lastOrNull()?.segmentation
    override fun convert(text: String): String {
        UTF8Util.validate(text)
        var current = text
        for (converter in children) current = converter.convert(current)
        return current
    }
    override fun inspect(text: String): ConversionInspectionResult {
        UTF8Util.validate(text)
        var current = text
        val trace = children.map { it.inspect(current).also { result -> current = result.output } }
        return ConversionInspectionResult(text, current, pipelineStages = trace)
    }
    override fun convertUtf8(input: ByteArray, startIndex: Int, endIndex: Int, mode: EncodedInputMode): ByteArray {
        var current = input.copyOfRange(startIndex, EncodedUTF8.end(input, startIndex, endIndex, mode))
        for (converter in children) current = converter.convertUtf8(current)
        return current
    }
    override fun inspectUtf8(input: ByteArray, startIndex: Int, endIndex: Int, mode: EncodedInputMode): EncodedInspectionResult {
        val snapshot = input.copyOfRange(startIndex, EncodedUTF8.end(input, startIndex, endIndex, mode))
        var current = snapshot
        val trace = children.map { it.inspectUtf8(current).also { result -> current = result.output } }
        return EncodedInspectionResult(snapshot, current, pipelineStages = trace)
    }
}

class ConfigBasedConverter(val normalization: Converter, val mainConverter: Converter) :
    PipelineConverter(listOf(normalization, mainConverter)) {
    override val segmentation: Segmentation? get() = mainConverter.segmentation
    override val conversionChain: ConversionChain? get() = mainConverter.conversionChain
}

/** Mutable, caller-owned stream. The keep-tail policy matches upstream's default 16 code points. */
class ConverterStream(val converter: Converter, val maxKeepChars: Int = 16) {
    init { require(maxKeepChars >= 0) }
    private var pending = ""
    fun convertChunk(input: String): String {
        pending += input
        val keepStart = flushableTextCount(pending, maxKeepChars)
        if (keepStart == 0) return ""
        val result = converter.convert(pending.substring(0, keepStart))
        pending = pending.substring(keepStart)
        return result
    }
    fun finish(input: String = ""): String {
        pending += input
        val result = if (pending.isEmpty()) "" else converter.convert(pending)
        pending = ""
        return result
    }
}

internal fun flushableTextCount(pending: String, maxKeepChars: Int): Int {
    // Retain a trailing high surrogate so callers can split a UTF-16 pair between chunks.
    val completeEnd = if (pending.lastOrNull()?.isHighSurrogate() == true) pending.length - 1 else pending.length
    UTF8Util.validate(pending.substring(0, completeEnd))
    var keepStart = completeEnd
    repeat(maxKeepChars) {
        if (keepStart > 0) {
            keepStart--
            if (pending[keepStart].isLowSurrogate()) keepStart--
        }
    }
    var candidate = completeEnd
    var count = 0
    while (candidate > 0 && count++ < 64) {
        candidate--
        if (pending[candidate].isLowSurrogate()) candidate--
        if (UTF8Util.idsEnd(pending, candidate, completeEnd) == -1) keepStart = minOf(keepStart, candidate)
    }
    return keepStart
}
