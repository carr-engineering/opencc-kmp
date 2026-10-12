/* Adapted from OpenCC UTF8Util, conversion and streaming contracts. Apache-2.0. */
package io.github.opencckmp

/** Explicit byte ranges or input ending at the first NUL within that range. */
enum class EncodedInputMode { LengthDelimited, NullTerminated }

internal object EncodedUTF8 {
    fun range(bytes: ByteArray, start: Int, end: Int) {
        require(start in 0..end && end <= bytes.size) { "Invalid byte range" }
    }
    fun end(bytes: ByteArray, start: Int, end: Int, mode: EncodedInputMode): Int {
        range(bytes, start, end)
        if (mode == EncodedInputMode.NullTerminated) for (i in start until end) if (bytes[i] == 0.toByte()) return i
        return end
    }
    fun width(lead: Int): Int = when {
        lead < 0x80 -> 1
        lead and 0xE0 == 0xC0 -> 2
        lead and 0xF0 == 0xE0 -> 3
        lead and 0xF8 == 0xF0 -> 4
        lead and 0xFC == 0xF8 -> 5
        lead and 0xFE == 0xFC -> 6
        else -> 0
    }
    fun width(bytes: ByteArray, start: Int): Int = width(bytes[start].toInt() and 255)
    fun maskedPoint(bytes: ByteArray, start: Int, width: Int): Int {
        if (width == 1) return bytes[start].toInt() and 255
        var point = bytes[start].toInt() and ((1 shl (7 - width)) - 1)
        for (i in 1 until width) point = (point shl 6) or (bytes[start + i].toInt() and 63)
        return point
    }
    /** Only canonical scalar encodings can match a Kotlin String key. Does not validate other bytes. */
    fun scalar(bytes: ByteArray, start: Int, end: Int): Int {
        val count = width(bytes, start)
        if (count == 0 || count > 4 || count > end - start) return -1
        for (i in 1 until count) if (bytes[start + i].toInt() and 0xC0 != 0x80) return -1
        val point = maskedPoint(bytes, start, count)
        val minimum = when (count) { 1 -> 0; 2 -> 0x80; 3 -> 0x800; else -> 0x10000 }
        return if (point < minimum || point > 0x10FFFF || point in 0xD800..0xDFFF) -1 else point
    }
    fun invalid(bytes: ByteArray, offset: Int): Nothing = throw InvalidUTF8("Invalid leading byte 0x" +
        (bytes[offset].toInt() and 255).toString(16).padStart(2, '0') + " at byte offset $offset")

    // Complete end offset, -1 incomplete, -2 invalid/non-IDS. Traversal mirrors native leading-byte
    // semantics, including legacy 5/6-byte sequences and masked non-canonical code points.
    fun idsEnd(bytes: ByteArray, start: Int, end: Int): Int {
        if (start >= end) return -2
        val width = width(bytes, start)
        if (width == 0 || width > end - start || UTF8Util.idsArity(maskedPoint(bytes, start, width)) == 0) return -2
        var points = 0
        fun consume(offset: Int, depth: Int): Int {
            if (offset >= end) return -1
            if (depth == 0 || points >= 64) return -2
            val count = width(bytes, offset)
            if (count == 0) return -2
            if (count > end - offset) return -1
            points++
            var next = offset + count
            repeat(UTF8Util.idsArity(maskedPoint(bytes, offset, count))) {
                next = consume(next, depth - 1)
                if (next < 0) return next
            }
            return next
        }
        return consume(start, 16)
    }
    fun unmatchedEnd(bytes: ByteArray, start: Int, end: Int): Int {
        val ids = idsEnd(bytes, start, end)
        if (ids > start) return ids
        val count = width(bytes, start)
        if (count == 0) invalid(bytes, start)
        return start + minOf(count, end - start)
    }
    fun previous(bytes: ByteArray, end: Int): Int {
        var start = end - 1
        var distance = 1
        while (start > 0 && distance < 6 && bytes[start].toInt() and 0xC0 == 0x80) { start--; distance++ }
        if (width(bytes, start) != distance) invalid(bytes, start)
        return start
    }
    fun flushable(bytes: ByteArray, keepChars: Int): Int {
        var completeEnd = 0
        while (completeEnd < bytes.size) {
            val count = width(bytes, completeEnd)
            if (count == 0) invalid(bytes, completeEnd)
            if (count > bytes.size - completeEnd) break
            completeEnd += count
        }
        var keepStart = completeEnd
        var kept = 0
        while (keepStart > 0 && kept++ < keepChars) keepStart = previous(bytes, keepStart)
        var candidate = completeEnd
        var scanned = 0
        while (candidate > 0 && scanned++ < 64) {
            candidate = previous(bytes, candidate)
            if (idsEnd(bytes, candidate, completeEnd) == -1) keepStart = minOf(keepStart, candidate)
        }
        return keepStart
    }
}

internal class ByteOutput(capacity: Int) {
    private var bytes = ByteArray(maxOf(16, capacity))
    private var size = 0
    private fun grow(count: Int) {
        require(count <= Int.MAX_VALUE - size) { "Encoded output exceeds Kotlin array capacity" }
        val required = size + count
        if (required > bytes.size) {
            val doubled = if (bytes.size > Int.MAX_VALUE / 2) Int.MAX_VALUE else bytes.size * 2
            bytes = bytes.copyOf(maxOf(required, doubled))
        }
    }
    fun append(input: ByteArray, start: Int = 0, end: Int = input.size) {
        grow(end - start)
        input.copyInto(bytes, size, start, end)
        size += end - start
    }
    fun append(text: String) {
        // At most four octets per scalar; grow lazily to avoid retaining an oversized output image.
        visitUtf8(text, 0, text.length) { byte -> grow(1); bytes[size++] = byte.toByte(); true }
    }
    fun result(): ByteArray = bytes.copyOf(size)
}

/** Mutable encoded session, retaining the same native byte/character/IDS window policy. */
class Utf8ConverterStream(val converter: Converter, val maxKeepChars: Int = 16) {
    init { require(maxKeepChars >= 0) }
    private var pending = byteArrayOf()
    fun convertChunk(input: ByteArray): ByteArray {
        pending += input
        val count = EncodedUTF8.flushable(pending, maxKeepChars)
        if (count == 0) return byteArrayOf()
        val output = converter.convertUtf8(pending, 0, count)
        pending = pending.copyOfRange(count, pending.size)
        return output
    }
    fun finish(input: ByteArray = byteArrayOf()): ByteArray {
        pending += input
        val output = if (pending.isEmpty()) byteArrayOf() else converter.convertUtf8(pending)
        pending = byteArrayOf()
        return output
    }
}

/** Arrays are caller-owned byte results; units remain encoded bytes rather than UTF-16 offsets. */
class EncodedInspectionStage(val index: Int, val segments: List<ByteArray>)
class EncodedInspectionResult(
    val input: ByteArray,
    val output: ByteArray,
    val segments: List<ByteArray> = emptyList(),
    val stages: List<EncodedInspectionStage> = emptyList(),
    val pipelineStages: List<EncodedInspectionResult> = emptyList(),
)

internal fun List<ByteArray>.nulTerminatedSnapshots(): List<ByteArray> = map {
    it.copyOfRange(0, EncodedUTF8.end(it, 0, it.size, EncodedInputMode.NullTerminated))
}
internal fun joinEncoded(segments: List<ByteArray>): ByteArray {
    val output = ByteOutput(0)
    for (segment in segments) output.append(segment)
    return output.result()
}
internal fun ConversionInspectionResult.toEncoded(): EncodedInspectionResult = EncodedInspectionResult(
    UTF8Util.encode(input), UTF8Util.encode(output), segments.map { UTF8Util.encode(it) },
    stages.map { stage -> EncodedInspectionStage(stage.index, stage.segments.map { UTF8Util.encode(it) }) },
    pipelineStages.map { it.toEncoded() },
)
