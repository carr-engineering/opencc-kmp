/* Adapted from OpenCC (Apache-2.0) and cppjieba (MIT); see licenses/CPPJIEBA-LICENSE. */
package io.github.opencckmp.jieba

import io.github.opencckmp.*

/** Immutable Jieba maximum-probability/HMM segmentation. Model text and dictionaries are caller-owned values. */
class JiebaSegmentation private constructor(private val words: WeightedTrie, private val model: HmmModel) : Segmentation {
    constructor(dictionary: Dict, model: String, userDictionaries: List<String> = emptyList()) : this(
        JiebaWords().apply { merged(dictionary) }.build(userDictionaries), HmmModel(model))

    override fun segment(text: String): Segments {
        UTF8Util.validate(text)
        val points = IntArray(UTF8Util.length(text))
        val offsets = IntArray(points.size + 1)
        var index = 0
        var cursor = 0
        while (cursor < text.length) {
            offsets[index] = cursor
            points[index++] = UTF8Util.codePoint(text, cursor)
            cursor = UTF8Util.nextIndex(text, cursor)
        }
        offsets[index] = cursor
        val segments = mutableListOf<String>()
        cut(points) { left, right -> segments.add(text.substring(offsets[left], offsets[right]).substringBefore('\u0000')) }
        return Segments(segments)
    }

    /** cppjieba's byte decoding is deliberately separate from strict Kotlin text decoding. */
    override fun segmentUtf8(input: ByteArray, startIndex: Int, endIndex: Int): List<ByteArray> {
        require(startIndex in 0..endIndex && endIndex <= input.size) { "Invalid byte range" }
        val points = IntArray(endIndex - startIndex)
        val offsets = IntArray(points.size + 1)
        var index = 0
        var cursor = startIndex
        while (cursor < endIndex) {
            val first = input[cursor].toInt() and 255
            val width = when {
                first < 128 -> 1
                first <= 0xdf && cursor + 1 < endIndex -> 2
                first <= 0xef && cursor + 2 < endIndex -> 3
                first <= 0xf7 && cursor + 3 < endIndex -> 4
                else -> return emptyList() // Native cppjieba clears the entire decoded sentence on failure.
            }
            var point = first and when (width) { 1 -> 127; 2 -> 31; 3 -> 15; else -> 7 }
            for (byte in 1 until width) point = (point shl 6) or (input[cursor + byte].toInt() and 63)
            points[index] = point
            offsets[index++] = cursor
            cursor += width
        }
        offsets[index] = cursor
        val segments = mutableListOf<ByteArray>()
        cut(points.copyOf(index)) { left, right ->
            var end = offsets[left]
            while (end < offsets[right] && input[end] != 0.toByte()) end++
            segments.add(input.copyOfRange(offsets[left], end))
        }
        return segments
    }

    private fun cut(points: IntArray, add: (Int, Int) -> Unit) {
        var from = 0
        while (from < points.size) {
            var until = from
            while (until < points.size && !separator(points[until])) until++
            if (until == from) until++
            mix(points, from, until, add)
            from = until
        }
    }

    private fun mix(points: IntArray, from: Int, until: Int, add: (Int, Int) -> Unit) {
        val length = until - from
        val scores = DoubleArray(length + 1)
        val ends = IntArray(length)
        for (position in length - 1 downTo 0) {
            var node = words.nextRune(0, points[from + position])
            var best = MIN_WEIGHT
            var chosen = position + 1
            val first = scores[position + 1] + words.weight(node)
            if (first > best) best = first
            // Prefixes are considered shortest first; strict improvement preserves native ties.
            var right = position + 1
            while (right < length && right - position < 512 && node >= 0) {
                node = words.nextRune(node, points[from + right])
                if (words.hasWord(node)) {
                    val candidate = scores[right + 1] + words.weight(node)
                    if (candidate > best) { best = candidate; chosen = right + 1 }
                }
                right++
            }
            scores[position] = best
            ends[position] = chosen
        }
        var position = 0
        while (position < length) {
            val right = ends[position]
            if (right != position + 1 || words.isUserSingle(points[from + position])) {
                add(from + position, from + right)
                position = right
            } else {
                var last = position + 1
                while (last < length && ends[last] == last + 1 && !words.isUserSingle(points[from + last])) last++
                model.cut(points, from + position, from + last, add)
                position = last
            }
        }
    }

    private fun separator(point: Int): Boolean = point == 32 || point == 9 || point == 10 || point == 0xff0c || point == 0x3002

    companion object {
        fun fromText(dictionary: String, model: String, userDictionaries: List<String> = emptyList()): JiebaSegmentation =
            JiebaSegmentation(JiebaWords().apply { text(dictionary) }.build(userDictionaries), HmmModel(model))
    }
}
