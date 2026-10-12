/* Adapted from OpenCC UTF8Util.hpp and UTF8StringSlice.hpp. Apache-2.0. */
package io.github.opencckmp

/** Previous Unicode scalar boundary, expressed in UTF-16 units. */
fun UTF8Util.previousIndex(text: String, endIndex: Int): Int {
    require(endIndex in 1..text.length) { "No previous scalar in range" }
    val start = if (text[endIndex - 1].isLowSurrogate()) endIndex - 2 else endIndex - 1
    if (start < 0 || nextIndex(text, start) != endIndex) throw InvalidUTF16(endIndex - 1)
    return start
}

/** Complete scalar prefix fitting [maxByteLength] UTF-8 bytes. Returned text uses Kotlin ownership. */
fun UTF8Util.truncate(text: String, maxByteLength: Int): String {
    require(maxByteLength >= 0)
    var index = 0
    var remaining = maxByteLength
    while (index < text.length) {
        val point = codePoint(text, index)
        val width = when { point < 0x80 -> 1; point < 0x800 -> 2; point < 0x10000 -> 3; else -> 4 }
        if (width > remaining) break
        remaining -= width
        index = nextIndex(text, index)
    }
    return text.substring(0, index)
}

/** Scalar-start byte offsets, with the final encoded size appended as an endpoint. */
fun UTF8Util.byteMap(text: String): IntArray {
    val map = IntArray(length(text) + 1)
    var index = 0
    var scalar = 0
    var bytes = 0
    while (index < text.length) {
        map[scalar++] = bytes
        val point = codePoint(text, index)
        val width = when { point < 0x80 -> 1; point < 0x800 -> 2; point < 0x10000 -> 3; else -> 4 }
        require(width <= Int.MAX_VALUE - bytes) { "Encoded text exceeds Kotlin array capacity" }
        bytes += width
        index = nextIndex(text, index)
    }
    map[scalar] = bytes
    return map
}

/** Common prefix endpoint in UTF-16 units; independent of native pointer identity. */
fun UTF8Util.commonPrefixEnd(left: String, right: String): Int {
    var index = 0
    while (index < left.length && index < right.length) {
        if (codePoint(left, index) != codePoint(right, index)) break
        index = nextIndex(left, index)
    }
    return index
}

/** Scalar lexicographic comparison from the end, without reversing surrogate halves. */
fun UTF8Util.reverseCompare(left: String, right: String): Int {
    var a = left.length
    var b = right.length
    while (a > 0 && b > 0) {
        a = previousIndex(left, a)
        b = previousIndex(right, b)
        val difference = codePoint(left, a) - codePoint(right, b)
        if (difference != 0) return difference.compareTo(0)
    }
    return a.compareTo(b)
}

/** The pinned release recognizes U+2FF0 through U+2FFF, with arities 1, 2 or 3. */
fun UTF8Util.ideographicDescriptionOperatorArity(codePoint: Int): Int = idsArity(codePoint)

/** Complete IDS endpoint in UTF-16 units, or [startIndex] when absent, incomplete or over the limits. */
fun UTF8Util.ideographicDescriptionEnd(text: String, startIndex: Int = 0, endIndex: Int = text.length): Int {
    requireRange(text, startIndex, endIndex)
    return idsEnd(text, startIndex, endIndex).takeIf { it > startIndex } ?: startIndex
}

fun UTF8Util.isIncompleteIdeographicDescriptionPrefix(text: String, startIndex: Int = 0, endIndex: Int = text.length): Boolean {
    requireRange(text, startIndex, endIndex)
    return idsEnd(text, startIndex, endIndex) == -1
}

fun UTF8Util.isVariationSelector(codePoint: Int): Boolean =
    codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF

fun UTF8Util.containsVariationSelector(text: String): Boolean {
    var index = 0
    while (index < text.length) {
        if (isVariationSelector(codePoint(text, index))) return true
        index = nextIndex(text, index)
    }
    return false
}

/** Byte endpoint after an optional EF BB BF signature. Does not decode the resource. */
fun UTF8Util.skipBom(bytes: ByteArray, startIndex: Int = 0, endIndex: Int = bytes.size): Int {
    EncodedUTF8.range(bytes, startIndex, endIndex)
    return if (endIndex - startIndex >= 3 && bytes[startIndex] == 0xEF.toByte() &&
        bytes[startIndex + 1] == 0xBB.toByte() && bytes[startIndex + 2] == 0xBF.toByte()) startIndex + 3 else startIndex
}

/** Native leading-byte width (1–6), or zero for an invalid lead; no canonical UTF-8 validation. */
fun UTF8Util.nextByteLengthOrZero(bytes: ByteArray, index: Int): Int = EncodedUTF8.width(bytes, index)

/** Native leading-byte width (1–6), throwing [InvalidUTF8] for an invalid lead. */
fun UTF8Util.nextByteLength(bytes: ByteArray, index: Int): Int =
    nextByteLengthOrZero(bytes, index).takeIf { it != 0 } ?: EncodedUTF8.invalid(bytes, index)

/** Bounded native traversal; missing bytes fail rather than moving outside the array. */
fun UTF8Util.nextByteIndex(bytes: ByteArray, index: Int, endIndex: Int = bytes.size): Int {
    require(index in 0 until endIndex && endIndex <= bytes.size)
    val width = nextByteLength(bytes, index)
    if (width > endIndex - index) EncodedUTF8.invalid(bytes, index, endIndex)
    return index + width
}

fun UTF8Util.previousByteIndex(bytes: ByteArray, endIndex: Int): Int {
    require(endIndex in 1..bytes.size)
    return EncodedUTF8.previous(bytes, endIndex)
}

/** Native character count: leading-byte traversal, not canonical scalar decoding. */
fun UTF8Util.encodedLength(bytes: ByteArray, startIndex: Int = 0, endIndex: Int = bytes.size,
    mode: EncodedInputMode = EncodedInputMode.NullTerminated): Int {
    val end = EncodedUTF8.end(bytes, startIndex, endIndex, mode)
    var index = startIndex
    var count = 0
    while (index < end) { index = nextByteIndex(bytes, index, end); count++ }
    return count
}

/** Complete native character prefix fitting a byte budget, preserving original encoded bytes. */
fun UTF8Util.truncateUtf8(bytes: ByteArray, maxByteLength: Int, startIndex: Int = 0,
    endIndex: Int = bytes.size, mode: EncodedInputMode = EncodedInputMode.NullTerminated): ByteArray {
    require(maxByteLength >= 0)
    val end = EncodedUTF8.end(bytes, startIndex, endIndex, mode)
    if (end - startIndex < maxByteLength) return bytes.copyOfRange(startIndex, end)
    var index = startIndex
    while (index < end) {
        val width = nextByteLength(bytes, index)
        if (width > maxByteLength - (index - startIndex)) break
        if (width > end - index) EncodedUTF8.invalid(bytes, index, end)
        index += width
    }
    return bytes.copyOfRange(startIndex, index)
}

/** Complete IDS endpoint in bytes, or [startIndex] when absent/incomplete/invalid. */
fun UTF8Util.ideographicDescriptionByteEnd(bytes: ByteArray, startIndex: Int = 0, endIndex: Int = bytes.size): Int {
    EncodedUTF8.range(bytes, startIndex, endIndex)
    return EncodedUTF8.idsEnd(bytes, startIndex, endIndex).takeIf { it > startIndex } ?: startIndex
}

fun UTF8Util.isIncompleteIdeographicDescriptionBytePrefix(bytes: ByteArray, startIndex: Int = 0,
    endIndex: Int = bytes.size): Boolean {
    EncodedUTF8.range(bytes, startIndex, endIndex)
    return EncodedUTF8.idsEnd(bytes, startIndex, endIndex) == -1
}

/** Pinned native scan: skips invalid leads, stops at truncated characters, masks continuation bytes. */
fun UTF8Util.containsVariationSelector(bytes: ByteArray, startIndex: Int = 0, endIndex: Int = bytes.size): Boolean {
    EncodedUTF8.range(bytes, startIndex, endIndex)
    var index = startIndex
    while (index < endIndex) {
        val width = EncodedUTF8.width(bytes, index)
        if (width == 0) { index++; continue }
        if (width > endIndex - index) return false
        if (isVariationSelector(EncodedUTF8.maskedPoint(bytes, index, width))) return true
        index += width
    }
    return false
}

/** Native character-start offsets relative to [startIndex]; the final endpoint is appended. */
fun UTF8Util.byteMap(bytes: ByteArray, startIndex: Int = 0, endIndex: Int = bytes.size,
    mode: EncodedInputMode = EncodedInputMode.NullTerminated): IntArray {
    val end = EncodedUTF8.end(bytes, startIndex, endIndex, mode)
    val result = IntArray(encodedLength(bytes, startIndex, end, EncodedInputMode.LengthDelimited) + 1)
    var index = startIndex
    var character = 0
    while (index < end) {
        result[character++] = index - startIndex
        index = nextByteIndex(bytes, index, end)
    }
    result[character] = end - startIndex
    return result
}

/** Index of an ASCII byte before CR, LF, NUL or [endIndex], traversing native character widths. */
fun UTF8Util.findNextInline(bytes: ByteArray, target: Byte, startIndex: Int = 0, endIndex: Int = bytes.size): Int {
    EncodedUTF8.range(bytes, startIndex, endIndex)
    var index = startIndex
    while (index < endIndex && bytes[index] != target && bytes[index] != 0.toByte() &&
        bytes[index] != '\n'.code.toByte() && bytes[index] != '\r'.code.toByte()) {
        index = nextByteIndex(bytes, index, endIndex)
    }
    return index
}
