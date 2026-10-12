/* Adapted from OpenCC (Apache-2.0) and cppjieba (MIT); see licenses/CPPJIEBA-LICENSE. */
package io.github.opencckmp.jieba

import io.github.opencckmp.*
import kotlin.math.ln

/** Packed UTF-16 arcs, traversed by scalar value; no objects are allocated while searching a DAG. */
internal class WeightedTrie(keys: List<String>, private val weights: DoubleArray, val minimum: Double, userSingles: Set<Int>) {
    private val starts: IntArray
    private val counts: IntArray
    private val terminals: IntArray
    private val labels: CharArray
    private val destinations: IntArray
    private val singles = userSingles.sorted().toIntArray()

    init {
        val ordered = keys.indices.sortedWith { a, b -> keys[a].compareTo(keys[b]) }
        fun shared(a: String, b: String): Int {
            var length = 0
            while (length < minOf(a.length, b.length) && a[length] == b[length]) length++
            return length
        }
        var size = 1
        var previous = ""
        for (id in ordered) {
            val key = keys[id]
            val added = key.length - shared(previous, key)
            if (added > Int.MAX_VALUE - size) throw InvalidFormat("Jieba dictionary exceeds Kotlin trie capacity")
            size += added
            previous = key
        }
        starts = IntArray(size)
        counts = IntArray(size)
        terminals = IntArray(size) { -1 }
        val parents = IntArray(size)
        val nodeLabels = CharArray(size)
        val path = IntArray((keys.maxOfOrNull { it.length } ?: 0) + 1)
        var node = 1
        previous = ""
        for (id in ordered) {
            val key = keys[id]
            val prefix = shared(previous, key)
            for (position in prefix until key.length) {
                val parent = path[position]
                parents[node] = parent
                nodeLabels[node] = key[position]
                counts[parent]++
                path[position + 1] = node++
            }
            terminals[path[key.length]] = id // Later user definitions replace earlier base definitions.
            previous = key
        }
        labels = CharArray(size - 1)
        destinations = IntArray(labels.size)
        var offset = 0
        for (index in 0 until size) { starts[index] = offset; offset += counts[index] }
        val filled = IntArray(size)
        for (child in 1 until size) {
            val parent = parents[child]
            val index = starts[parent] + filled[parent]++
            labels[index] = nodeLabels[child]
            destinations[index] = child
        }
    }

    private fun next(node: Int, label: Char): Int {
        var low = starts[node]
        var high = low + counts[node] - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            when {
                labels[middle] < label -> low = middle + 1
                labels[middle] > label -> high = middle - 1
                else -> return destinations[middle]
            }
        }
        return -1
    }

    fun nextRune(node: Int, point: Int): Int {
        if (node < 0 || point > 0x10ffff) return -1
        if (point < 0x10000) return next(node, point.toChar())
        val high = next(node, (0xd800 + ((point - 0x10000) ushr 10)).toChar())
        return if (high < 0) -1 else next(high, (0xdc00 + ((point - 0x10000) and 1023)).toChar())
    }
    fun weight(node: Int): Double = if (node < 0 || terminals[node] < 0) minimum else weights[terminals[node]]
    fun hasWord(node: Int): Boolean = node >= 0 && terminals[node] >= 0
    fun isUserSingle(point: Int): Boolean = findSorted(singles, point) >= 0
}

internal class JiebaWords {
    private val keys = mutableListOf<String>()
    private var frequencies = DoubleArray(256)
    private var kinds = ByteArray(256)

    private fun add(key: String, frequency: Double, base: Boolean, default: Boolean) {
        if (keys.size == frequencies.size) {
            if (frequencies.size > Int.MAX_VALUE / 2) throw InvalidFormat("Jieba dictionary exceeds Kotlin capacity")
            frequencies = frequencies.copyOf(frequencies.size * 2)
            kinds = kinds.copyOf(frequencies.size)
        }
        frequencies[keys.size] = frequency
        kinds[keys.size] = ((if (base) 1 else 0) or (if (default) 2 else 0)).toByte()
        keys.add(key)
    }

    fun merged(dict: Dict) {
        for (entry in dict.lexicon) {
            val values = entry.values
            var base = false
            var default = false
            var frequency = 0.0
            when (values.size) {
                3 -> when (values[2]) {
                    "base" -> { base = true; frequency = positive(values[0], entry.key) }
                    "user_freq" -> { frequency = nonnegative(values[0], entry.key); default = frequency == 0.0 }
                    "user_default" -> default = true
                    else -> throw InvalidFormat("unknown merged jieba entry kind for: ${entry.key}")
                }
                2 -> { base = true; frequency = positive(values[0], entry.key) }
                1 -> default = true
                else -> throw InvalidFormat("invalid merged jieba values for: ${entry.key}")
            }
            add(entry.key, frequency, base, default)
        }
    }

    fun text(text: String) {
        for (line in fileLines(text)) {
            val fields = splitFields(line, ' ')
            if (fields.size != 3) throw InvalidFormat("Invalid Jieba dictionary line: $line")
            UTF8Util.validate(fields[0])
            add(fields[0], cDouble(fields[1]), true, false)
        }
    }

    fun build(userDictionaries: List<String>): WeightedTrie {
        if (keys.isEmpty()) throw InvalidFormat("merged jieba dictionary is empty")
        var sum = 0.0
        var baseCount = 0
        for (index in keys.indices) if (kinds[index].toInt() and 1 != 0) { sum += frequencies[index]; baseCount++ }
        if (sum <= 0.0) throw InvalidFormat("merged jieba dictionary has no base frequencies")
        if (baseCount == 0) throw InvalidFormat("merged jieba dictionary has no weights for default entries")
        val base = DoubleArray(baseCount)
        var filled = 0
        for (index in keys.indices) if (kinds[index].toInt() and 1 != 0) base[filled++] = ln(frequencies[index] / sum)
        base.sort()
        val median = base[base.size / 2]
        val singles = mutableSetOf<Int>()
        val words = keys.toMutableList()
        var weights = DoubleArray(frequencies.size)
        for (index in keys.indices) {
            weights[index] = if (kinds[index].toInt() and 2 != 0) median else ln(frequencies[index] / sum)
            if (kinds[index].toInt() and 1 == 0 && UTF8Util.length(keys[index]) == 1) singles.add(UTF8Util.codePoint(keys[index], 0))
        }
        for (text in userDictionaries) for (line in fileLines(text)) {
            if (line.isEmpty()) continue
            val fields = splitFields(line, ' ')
            if (fields.size !in 1..3) continue // cppjieba inserts an empty node, which cannot match.
            val key = fields[0]
            UTF8Util.validate(key)
            if (words.size == weights.size) {
                if (weights.size > Int.MAX_VALUE / 2) throw InvalidFormat("Jieba dictionary exceeds Kotlin capacity")
                weights = weights.copyOf(weights.size * 2)
            }
            weights[words.size] = if (fields.size == 3) ln(cInt(fields[1]).toDouble() / sum) else median
            words.add(key)
            if (UTF8Util.length(key) == 1) singles.add(UTF8Util.codePoint(key, 0))
        }
        return WeightedTrie(words, weights.copyOf(words.size), base.first(), singles)
    }

    private fun positive(value: String, key: String): Double =
        value.trimStart().toDoubleOrNull()?.takeUnless { it <= 0.0 }
            ?: throw InvalidFormat("invalid merged jieba base frequency for: $key")
    private fun nonnegative(value: String, key: String): Double =
        value.trimStart().toDoubleOrNull()?.takeUnless { it < 0.0 }
            ?: throw InvalidFormat("invalid merged jieba user frequency for: $key")
}

internal fun fileLines(text: String): List<String> = text.split('\n').let { if (text.endsWith('\n')) it.dropLast(1) else it }
internal fun splitFields(text: String, separator: Char): List<String> =
    if (text.isEmpty()) emptyList() else text.split(separator).let { if (text.endsWith(separator)) it.dropLast(1) else it }
private val integerPrefix = Regex("^[\\t\\r\\n ]*([+-]?[0-9]+)")
private val doublePrefix = Regex("^[\\t\\r\\n ]*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|[+-]?(?:inf(?:inity)?|nan))", RegexOption.IGNORE_CASE)
internal fun cInt(text: String): Int = text.toIntOrNull() ?: integerPrefix.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
internal fun cDouble(text: String): Double {
    text.toDoubleOrNull()?.let { return it }
    val token = doublePrefix.find(text)?.groupValues?.get(1) ?: return 0.0
    return when (token.lowercase()) { "inf", "+inf", "infinity", "+infinity" -> Double.POSITIVE_INFINITY
        "-inf", "-infinity" -> Double.NEGATIVE_INFINITY; "nan", "+nan", "-nan" -> Double.NaN
        else -> token.toDoubleOrNull() ?: 0.0 }
}

internal fun findSorted(values: IntArray, value: Int): Int {
    var low = 0
    var high = values.size - 1
    while (low <= high) {
        val middle = (low + high) ushr 1
        when {
            values[middle] < value -> low = middle + 1
            values[middle] > value -> high = middle - 1
            else -> return middle
        }
    }
    return -1
}
