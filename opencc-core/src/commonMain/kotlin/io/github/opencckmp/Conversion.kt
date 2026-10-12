/* Adapted from OpenCC. Copyright 2010-2026 Carbo Kuo and contributors. Apache-2.0. */
package io.github.opencckmp

interface Segmentation {
    fun segment(text: String): Segments
}

class Segments(values: List<String>) : Iterable<String> {
    private val stored = values.toList()
    val values: List<String> get() = stored.toList()
    val size: Int get() = stored.size
    operator fun get(index: Int): String = stored[index]
    override fun iterator(): Iterator<String> = stored.iterator()
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
    fun convert(segments: Segments): Segments = Segments(segments.map { convert(it) })
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
    /** Encoded-input range units and returned data are UTF-8 bytes. No implicit NUL truncation. */
    fun convertUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size): ByteArray =
        UTF8Util.encode(convert(UTF8Util.decode(input, startIndex, endIndex)))
    fun convertUtf8To(input: ByteArray, output: ByteArray, offset: Int = 0): Int {
        require(offset in 0..output.size) { "Invalid output offset" }
        val converted = convertUtf8(input)
        require(converted.size <= output.size - offset) { "Insufficient output capacity" }
        converted.copyInto(output, offset)
        return converted.size
    }
}

class SingleStageConverter(
    override val segmentation: Segmentation?,
    override val conversionChain: ConversionChain,
) : Converter {
    override fun convert(text: String): String {
        UTF8Util.validate(text)
        if (segmentation == null) return conversionChain.convert(text)
        return buildString(text.length) {
            for (segment in segmentation.segment(text)) append(conversionChain.convert(segment))
        }
    }
    override fun inspect(text: String): ConversionInspectionResult {
        UTF8Util.validate(text)
        val initial = segmentation?.segment(text) ?: Segments(listOf(text))
        val trace = conversionChain.convertWithTrace(initial)
        return ConversionInspectionResult(text, trace.lastOrNull()?.toString() ?: initial.toString(),
            initial.values, trace.mapIndexed { index, segments -> ConversionInspectionStage(index + 1, segments.values) })
    }
}

open class PipelineConverter(converters: List<Converter>) : Converter {
    protected val children = converters.toList()
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
        if (keepStart == 0) return ""
        val result = converter.convert(pending.substring(0, keepStart))
        pending = pending.substring(keepStart)
        return result
    }
    fun finish(input: String = ""): String {
        pending += input
        val result = converter.convert(pending)
        pending = ""
        return result
    }
}
