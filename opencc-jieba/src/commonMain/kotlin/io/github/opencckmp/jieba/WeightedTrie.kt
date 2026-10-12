/* Adapted from OpenCC (Apache-2.0) and cppjieba (MIT); see licenses/CPPJIEBA-LICENSE. */
package io.github.opencckmp.jieba

import io.github.opencckmp.*
import kotlin.math.ln

/** Packed cppjieba Rune arcs; no objects are allocated while searching a DAG. */
internal class WeightedTrie(keys: List<IntArray>, private val weights: DoubleArray, val minimum: Double, userSingles: Set<Int>) {
    private val starts: IntArray
    private val counts: IntArray
    private val terminals: IntArray
    private val labels: IntArray
    private val destinations: IntArray
    private val singles = userSingles.sorted().toIntArray()

    init {
        val ordered = keys.indices.sortedWith { a, b ->
            val first = keys[a]
            val second = keys[b]
            var comparison = 0
            var index = 0
            while (index < minOf(first.size, second.size) && comparison == 0) {
                comparison = first[index].compareTo(second[index])
                index++
            }
            if (comparison == 0) comparison = first.size.compareTo(second.size)
            if (comparison == 0) a.compareTo(b) else comparison
        }
        fun shared(a: IntArray, b: IntArray): Int {
            var length = 0
            while (length < minOf(a.size, b.size) && a[length] == b[length]) length++
            return length
        }
        var size = 1
        var previous = IntArray(0)
        for (id in ordered) {
            val key = keys[id]
            val added = key.size - shared(previous, key)
            if (added > Int.MAX_VALUE - size) throw InvalidFormat("Jieba dictionary exceeds Kotlin trie capacity")
            size += added
            previous = key
        }
        starts = IntArray(size)
        counts = IntArray(size)
        terminals = IntArray(size) { -1 }
        val parents = IntArray(size)
        val nodeLabels = IntArray(size)
        val path = IntArray((keys.maxOfOrNull { it.size } ?: 0) + 1)
        var node = 1
        previous = IntArray(0)
        for (id in ordered) {
            val key = keys[id]
            val prefix = shared(previous, key)
            for (position in prefix until key.size) {
                val parent = path[position]
                parents[node] = parent
                nodeLabels[node] = key[position]
                counts[parent]++
                path[position + 1] = node++
            }
            terminals[path[key.size]] = id // Later user definitions replace earlier base definitions.
            previous = key
        }
        labels = IntArray(size - 1)
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

    private fun next(node: Int, label: Int): Int {
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

    fun nextRune(node: Int, point: Int): Int = if (node < 0) -1 else next(node, point)
    fun weight(node: Int): Double = if (node < 0 || terminals[node] < 0) minimum else weights[terminals[node]]
    fun hasWord(node: Int): Boolean = node >= 0 && terminals[node] >= 0
    fun isUserSingle(point: Int): Boolean = findSorted(singles, point) >= 0
}

internal class JiebaWords {
    private val keys = mutableListOf<IntArray>()
    private var frequencies = DoubleArray(256)
    private var kinds = ByteArray(256)

    private fun add(key: IntArray, frequency: Double, base: Boolean, default: Boolean) {
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
            val encoded = UTF8Util.encode(entry.key)
            add(decodeResourceRunes(encoded, 0, encoded.size)!!, frequency, base, default)
        }
    }

    fun text(text: String) = text(UTF8Util.encode(text))

    fun text(bytes: ByteArray) {
        forEachResourceLine(bytes) { start, end ->
            val fields = resourceFields(bytes, start, end, 32)
            if (fields.size != 6) throw InvalidFormat("Invalid Jieba dictionary line: ${bytes.decodeToString(start, end)}")
            val key = decodeResourceRunes(bytes, fields[0], fields[1])
                ?: throw InvalidFormat("Invalid Jieba dictionary word encoding")
            add(key, cDouble(bytes.decodeToString(fields[2], fields[3])), true, false)
        }
    }

    fun build(userDictionaries: List<String>): WeightedTrie = buildEncoded(userDictionaries.map(UTF8Util::encode))

    fun buildEncoded(userDictionaries: List<ByteArray>): WeightedTrie {
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
            val userEntry = kinds[index].toInt() and 1 == 0 &&
                (kinds[index].toInt() and 2 != 0 || frequencies[index] >= 0.0)
            if (userEntry && keys[index].size == 1) singles.add(keys[index][0])
        }
        for (bytes in userDictionaries) forEachResourceLine(bytes) { start, end ->
            if (start != end) {
                val fields = resourceFields(bytes, start, end, 32)
                // Failed native MakeNodeInfo leaves an empty node that cannot match input.
                val key = if (fields.size in 2..6) decodeResourceRunes(bytes, fields[0], fields[1]) else null
                if (key != null) {
                    if (words.size == weights.size) {
                        if (weights.size > Int.MAX_VALUE / 2) throw InvalidFormat("Jieba dictionary exceeds Kotlin capacity")
                        weights = weights.copyOf(weights.size * 2)
                    }
                    weights[words.size] = if (fields.size == 6) ln(cInt(bytes.decodeToString(fields[2], fields[3])).toDouble() / sum) else median
                    words.add(key)
                    if (key.size == 1) singles.add(key[0])
                }
            }
        }
        return WeightedTrie(words, weights.copyOf(words.size), base.first(), singles)
    }

    private fun positive(value: String, key: String): Double =
        cDoubleComplete(value)?.takeUnless { it <= 0.0 }
            ?: throw InvalidFormat("invalid merged jieba base frequency for: $key")
    private fun nonnegative(value: String, key: String): Double =
        cDoubleComplete(value)?.takeUnless { it < 0.0 }
            ?: throw InvalidFormat("invalid merged jieba user frequency for: $key")
}

internal fun fileLines(text: String): List<String> = text.split('\n').let { if (text.endsWith('\n')) it.dropLast(1) else it }
internal fun splitFields(text: String, separator: Char): List<String> =
    if (text.isEmpty()) emptyList() else text.split(separator).let { if (text.endsWith(separator)) it.dropLast(1) else it }
private val integerPrefix = Regex("^[\\t\\r\\n\\u000b\\u000c ]*([+-]?[0-9]+)")
private val doublePrefix = Regex("^[\\t\\r\\n\\u000b\\u000c ]*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|[+-]?(?:inf(?:inity)?|nan))", RegexOption.IGNORE_CASE)
internal fun cInt(text: String): Int = text.toIntOrNull() ?: integerPrefix.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
internal fun cDouble(text: String): Double {
    var begin = 0
    while (begin < text.length && text[begin] in " \t\r\n\u000b\u000c") begin++
    val sign = if (text.getOrNull(begin) == '+' || text.getOrNull(begin) == '-') 1 else 0
    if (text.getOrNull(begin + sign) == '0' && text.getOrNull(begin + sign + 1) in listOf('x', 'X')) {
        return hexDouble(text, begin)
    }
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
