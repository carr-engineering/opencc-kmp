/* Adapted from OpenCC PrefixMatch.cpp / Utf8SkipScan.hpp. Apache-2.0. */
package io.github.opencckmp

/**
 * Immutable caller-owned index for repeated unmatched-prefix scans. Construction reads dictionary
 * keys; subsequent scans allocate no substrings or lookup results. No global cache is retained.
 * A candidate first character stops the scan even when the rest of its key would not match.
 */
class PrefixScan(dict: Dict) {
    private val leads = BooleanArray(256)
    private val bmp = LongArray(1024)
    init {
        fun mark(point: Int) {
            val lead = when {
                point < 0x80 -> point
                point < 0x800 -> 0xC0 or (point ushr 6)
                point < 0x10000 -> 0xE0 or (point ushr 12)
                else -> 0xF0 or (point ushr 18)
            }
            leads[lead] = true
            if (point in 0x80..0xFFFF) bmp[point ushr 6] = bmp[point ushr 6] or (1L shl (point and 63))
        }
        fun collect(dictionary: Dict) {
            val children = dictionary.groupItems
            if (children != null) for (child in children) collect(child)
            else for (entry in dictionary.lexicon) if (entry.key.isNotEmpty()) mark(UTF8Util.codePoint(entry.key, 0))
        }
        collect(dict)
        for (point in 0x2FF0..0x2FFF) if (UTF8Util.idsArity(point) != 0) mark(point)
    }
    private fun candidate(point: Int): Boolean = when {
        point < 0x80 -> leads[point]
        point < 0x10000 -> bmp[point ushr 6] and (1L shl (point and 63)) != 0L
        else -> leads[0xF0 or (point ushr 18)]
    }

    /** Number of leading UTF16 units safe to consume without a dictionary lookup. IDS stops scans. */
    fun skipUnmatchable(text: String, startIndex: Int = 0, endIndex: Int = text.length): Int {
        UTF8Util.requireRange(text, startIndex, endIndex)
        var index = startIndex
        while (index < endIndex) {
            if (candidate(UTF8Util.codePoint(text, index))) break
            index = UTF8Util.nextIndex(text, index)
        }
        return index - startIndex
    }

    /**
     * Number of leading bytes safe to consume, matching native lead/masked-BMP traversal.
     * Stops at invalid leads, truncated sequences and IDS operators; includes bounded NUL bytes.
     * Supplementary and legacy characters use conservative leading-byte candidate filtering.
     */
    fun skipUnmatchableUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size): Int {
        EncodedUTF8.range(input, startIndex, endIndex)
        var index = startIndex
        while (index < endIndex) {
            val lead = input[index].toInt() and 255
            val width = EncodedUTF8.width(lead)
            if (width == 0 || width > endIndex - index) break
            val stop = if (width == 2 || width == 3) {
                val point = EncodedUTF8.maskedPoint(input, index, width)
                bmp[point ushr 6] and (1L shl (point and 63)) != 0L
            } else leads[lead]
            if (stop) break
            index += width
        }
        return index - startIndex
    }
}

/** Convenience scan; retain a [PrefixScan] to reuse its immutable index across calls. */
fun Dict.skipUnmatchable(text: String, startIndex: Int = 0, endIndex: Int = text.length): Int =
    PrefixScan(this).skipUnmatchable(text, startIndex, endIndex)

/** Byte equivalent of [skipUnmatchable], preserving native non-canonical traversal and byte units. */
fun Dict.skipUnmatchableUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size): Int =
    PrefixScan(this).skipUnmatchableUtf8(input, startIndex, endIndex)
