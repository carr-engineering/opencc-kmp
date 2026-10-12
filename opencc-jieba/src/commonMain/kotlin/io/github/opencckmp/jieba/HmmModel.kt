/* Adapted from cppjieba (MIT); see licenses/CPPJIEBA-LICENSE. */
package io.github.opencckmp.jieba

import io.github.opencckmp.*

internal const val MIN_WEIGHT = -3.14e100

internal class HmmModel(bytes: ByteArray) {
    constructor(text: String) : this(UTF8Util.encode(text))
    private val start: DoubleArray
    private val transitions: Array<DoubleArray>
    private val emissions: Array<Emission>

    init {
        // Preserve raw emission bytes: cppjieba decodes masked/noncanonical rune encodings,
        // including rune values that cannot be represented by a Kotlin Unicode scalar.
        val lines = splitBytes(bytes, '\n'.code.toByte()).map(::trimBytes)
            .filter { it.isNotEmpty() && it[0] != '#'.code.toByte() }
        if (lines.size < 9) throw InvalidFormat("Incomplete Jieba HMM model")
        fun probabilities(line: ByteArray): DoubleArray {
            val fields = splitBytes(line, ' '.code.toByte())
            if (fields.size != 4) throw InvalidFormat("Jieba HMM probability row must contain four states")
            return DoubleArray(4) { cDouble(fields[it].decodeToString()) }
        }
        start = probabilities(lines[0])
        transitions = Array(4) { probabilities(lines[it + 1]) }
        emissions = Array(4) { state ->
            val values = mutableMapOf<Int, Double>()
            for (field in splitBytes(lines[state + 5], ','.code.toByte())) {
                val parts = splitBytes(field, ':'.code.toByte())
                if (parts.size != 2) throw InvalidFormat("Invalid Jieba HMM emission: ${field.decodeToString()}")
                values[emissionRune(parts[0])] = cDouble(parts[1].decodeToString())
            }
            Emission(values)
        }
    }

    private fun emissionRune(bytes: ByteArray): Int {
        if (bytes.isEmpty()) throw InvalidFormat("Empty Jieba HMM emission key")
        val first = bytes[0].toInt() and 255
        val width = when {
            first < 128 -> 1
            first <= 0xdf && bytes.size >= 2 -> 2
            first <= 0xef && bytes.size >= 3 -> 3
            first <= 0xf7 && bytes.size >= 4 -> 4
            else -> throw InvalidFormat("Invalid Jieba HMM emission key encoding")
        }
        if (bytes.size != width) throw InvalidFormat("Jieba HMM emission key must contain one rune")
        var point = first and when (width) { 1 -> 127; 2 -> 31; 3 -> 15; else -> 7 }
        for (index in 1 until width) point = (point shl 6) or (bytes[index].toInt() and 63)
        return point
    }

    private fun splitBytes(bytes: ByteArray, separator: Byte): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var start = 0
        while (start < bytes.size) {
            var end = start
            while (end < bytes.size && bytes[end] != separator) end++
            result.add(bytes.copyOfRange(start, end))
            start = end + 1
        }
        return result
    }

    private fun trimBytes(bytes: ByteArray): ByteArray {
        fun whitespace(value: Byte): Boolean = value == 32.toByte() || value.toInt() in 9..13
        var start = 0
        var end = bytes.size
        while (start < end && whitespace(bytes[start])) start++
        while (end > start && whitespace(bytes[end - 1])) end--
        return bytes.copyOfRange(start, end)
    }

    private class Emission(values: Map<Int, Double>) {
        private val keys = values.keys.sorted().toIntArray()
        private val probabilities = DoubleArray(keys.size) { values.getValue(keys[it]) }
        fun get(point: Int): Double {
            val index = findSorted(keys, point)
            return if (index < 0) MIN_WEIGHT else probabilities[index]
        }
    }

    fun cut(points: IntArray, from: Int, until: Int, add: (Int, Int) -> Unit) {
        var left = from
        var right = from
        while (right < until) {
            if (points[right] < 0x80) {
                if (left != right) viterbi(points, left, right, add)
                left = right
                right++
                if (alphanumeric(points[left])) {
                    while (right < until && alphanumeric(points[right])) right++
                    if (right + 1 < until && points[right] == '.'.code && digit(points[right + 1])) {
                        right += 2
                        while (right < until && digit(points[right])) right++
                    }
                }
                add(left, right)
                left = right
            } else right++
        }
        if (left != right) viterbi(points, left, right, add)
    }

    private fun viterbi(points: IntArray, from: Int, until: Int, add: (Int, Int) -> Unit) {
        val length = until - from
        if (length > Int.MAX_VALUE / 4) throw InvalidFormat("Jieba input exceeds Kotlin HMM capacity")
        val paths = IntArray(length * 4)
        var previous = DoubleArray(4) { state -> start[state] + emissions[state].get(points[from]) }
        var current = DoubleArray(4)
        for (position in 1 until length) {
            for (state in 0..3) {
                var best = MIN_WEIGHT
                var chosen = 1 // Native fallback is E when no predecessor improves the sentinel.
                val emission = emissions[state].get(points[from + position])
                for (prior in 0..3) {
                    val candidate = previous[prior] + transitions[prior][state] + emission
                    if (candidate > best) { best = candidate; chosen = prior }
                }
                current[state] = best
                paths[position * 4 + state] = chosen
            }
            val swap = previous; previous = current; current = swap
        }
        var state = if (previous[1] >= previous[3]) 1 else 3
        val states = IntArray(length)
        for (position in length - 1 downTo 0) { states[position] = state; state = paths[position * 4 + state] }
        var left = from
        for (position in states.indices) if (states[position] % 2 == 1) {
            val right = from + position + 1
            add(left, right)
            left = right
        }
    }
    private fun digit(point: Int): Boolean = point in '0'.code..'9'.code
    private fun alphanumeric(point: Int): Boolean = digit(point) || point in 'a'.code..'z'.code || point in 'A'.code..'Z'.code
}
