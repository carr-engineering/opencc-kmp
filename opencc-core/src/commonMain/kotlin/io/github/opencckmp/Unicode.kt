package io.github.opencckmp

/** Encoding helpers use standard Kotlin types; text offsets are UTF-16 code units. */
object UTF8Util {
    fun decode(bytes: ByteArray, startIndex: Int = 0, endIndex: Int = bytes.size): String {
        require(startIndex in 0..endIndex && endIndex <= bytes.size) { "Invalid byte range" }
        return try {
            bytes.decodeToString(startIndex, endIndex, throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            throw InvalidUTF8("Malformed encoded input", e)
        }
    }

    fun encode(text: String): ByteArray {
        validate(text)
        return text.encodeToByteArray(throwOnInvalidSequence = true)
    }

    fun validate(text: String) {
        var i = 0
        while (i < text.length) i = nextIndex(text, i)
    }

    fun nextIndex(text: String, index: Int): Int {
        val char = text[index]
        if (char.isHighSurrogate()) {
            if (index + 1 >= text.length || !text[index + 1].isLowSurrogate()) throw InvalidUTF16(index)
            return index + 2
        }
        if (char.isLowSurrogate()) throw InvalidUTF16(index)
        return index + 1
    }

    fun codePoint(text: String, index: Int): Int {
        val end = nextIndex(text, index)
        return if (end == index + 1) text[index].code
        else 0x10000 + ((text[index].code - 0xD800) shl 10) + text[index + 1].code - 0xDC00
    }

    fun length(text: String): Int {
        var count = 0
        var i = 0
        while (i < text.length) { i = nextIndex(text, i); count++ }
        return count
    }

    /** Valid Unicode scalar order equals unsigned UTF-8 lexicographic order. */
    fun compare(left: String, right: String): Int {
        var a = 0
        var b = 0
        while (a < left.length && b < right.length) {
            val difference = codePoint(left, a) - codePoint(right, b)
            if (difference != 0) return difference
            a = nextIndex(left, a)
            b = nextIndex(right, b)
        }
        return (left.length - a).compareTo(right.length - b)
    }

    internal fun requireRange(text: String, start: Int, end: Int) {
        require(start in 0..end && end <= text.length) { "Invalid UTF-16 range" }
        require(start == 0 || start == text.length || !text[start].isLowSurrogate()) { "Range splits a surrogate pair" }
        require(end == 0 || end == text.length || !text[end].isLowSurrogate()) { "Range splits a surrogate pair" }
    }

    internal fun idsArity(codePoint: Int): Int = when (codePoint) {
        0x2FF2, 0x2FF3 -> 3
        0x2FFE, 0x2FFF -> 1
        in 0x2FF0..0x2FFD -> 2
        else -> 0
    }

    // Positive = complete end offset; -1 = incomplete; -2 = invalid/non-IDS.
    internal fun idsEnd(text: String, start: Int, limit: Int = text.length): Int {
        if (start >= limit || idsArity(codePoint(text, start)) == 0) return -2
        var points = 0
        fun consume(offset: Int, depth: Int): Int {
            if (offset >= limit) return -1
            if (depth == 0 || points >= 64) return -2
            points++
            var end = nextIndex(text, offset)
            if (end > limit) return -1
            repeat(idsArity(codePoint(text, offset))) {
                end = consume(end, depth - 1)
                if (end < 0) return end
            }
            return end
        }
        return consume(start, 16)
    }

    internal fun unmatchedEnd(text: String, start: Int, end: Int): Int =
        idsEnd(text, start, end).takeIf { it > start } ?: nextIndex(text, start)
}

