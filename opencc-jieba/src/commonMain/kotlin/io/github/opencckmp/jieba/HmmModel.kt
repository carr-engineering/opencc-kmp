/* Adapted from cppjieba (MIT); see licenses/CPPJIEBA-LICENSE. */
package io.github.opencckmp.jieba

import io.github.opencckmp.*

internal const val MIN_WEIGHT = -3.14e100

internal class HmmModel(text: String) {
    private val start: DoubleArray
    private val transitions: Array<DoubleArray>
    private val emissions: Array<Emission>

    init {
        val lines = fileLines(text).map { it.trim { c -> c in " \t\r\n\u000b\u000c" } }
            .filter { it.isNotEmpty() && !it.startsWith('#') }
        if (lines.size < 9) throw InvalidFormat("Incomplete Jieba HMM model")
        fun probabilities(line: String): DoubleArray {
            val fields = splitFields(line, ' ')
            if (fields.size != 4) throw InvalidFormat("Jieba HMM probability row must contain four states")
            return DoubleArray(4) { cDouble(fields[it]) }
        }
        start = probabilities(lines[0])
        transitions = Array(4) { probabilities(lines[it + 1]) }
        emissions = Array(4) { state ->
            val values = mutableMapOf<Int, Double>()
            for (field in splitFields(lines[state + 5], ',')) {
                val parts = splitFields(field, ':')
                if (parts.size != 2 || UTF8Util.length(parts[0]) != 1) throw InvalidFormat("Invalid Jieba HMM emission: $field")
                values[UTF8Util.codePoint(parts[0], 0)] = cDouble(parts[1])
            }
            Emission(values)
        }
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
