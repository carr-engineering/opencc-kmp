package io.github.opencckmp

/** A key with ordered conversion candidates. The first candidate is the default. */
class DictEntry(val key: String, values: List<String>) {
    private val storedValues = values.toList()
    val values: List<String> get() = storedValues.toList()
    val defaultValue: String = storedValues.firstOrNull() ?: key
    val keyLength: Int get() = key.length
    val keyByteLength: Int = UTF8Util.byteLength(key)
    val numValues: Int get() = storedValues.size
    override fun toString(): String = key + "\t" + storedValues.joinToString(" ")
}

class Lexicon(entries: List<DictEntry>) : Iterable<DictEntry> {
    private val storedEntries = entries.toList()
    val entries: List<DictEntry> get() = storedEntries.toList()
    val size: Int get() = storedEntries.size
    operator fun get(index: Int): DictEntry = storedEntries[index]
    override fun iterator(): Iterator<DictEntry> = readOnlyIterator(storedEntries)
    fun sorted(): Lexicon = Lexicon(storedEntries.sortedWith { a, b -> UTF8Util.compare(a.key, b.key) })
    fun isSorted(): Boolean = storedEntries.zipWithNext().all { (a, b) -> UTF8Util.compare(a.key, b.key) <= 0 }
    fun isUnique(): Boolean = storedEntries.map { it.key }.toSet().size == storedEntries.size

    companion object {
        fun parse(text: String, keyValueDelimiter: Char = '\t'): Lexicon {
            UTF8Util.validate(text)
            val entries = mutableListOf<DictEntry>()
            text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, rawLine ->
                val line = rawLine.substringBefore('\u0000').substringBefore('\r')
                if (line.isNotEmpty() && !line.startsWith('#')) {
                    val separator = line.indexOf(keyValueDelimiter)
                    if (separator < 0) throw InvalidTextDictionary("Tabular not found $line", index + 1)
                    entries.add(DictEntry(line.substring(0, separator), line.substring(separator + 1).split(' ')))
                }
            }
            return Lexicon(entries)
        }
    }
}

/** Do not leak a platform collection's mutable iterator through an immutable public value. */
internal fun <T> readOnlyIterator(values: List<T>): Iterator<T> = object : Iterator<T> {
    private var index = 0
    override fun hasNext(): Boolean = index < values.size
    override fun next(): T {
        if (!hasNext()) throw NoSuchElementException()
        return values[index++]
    }
}

interface Dict {
    val keyMaxLength: Int
    val lexicon: Lexicon
    val groupItems: List<Dict>? get() = null
    val matchPolicy: DictGroupMatchPolicy get() = DictGroupMatchPolicy.ShortCircuit
    fun matchPrefix(text: String, startIndex: Int = 0, endIndex: Int = text.length): DictEntry?
    fun matchAllPrefixes(text: String, startIndex: Int = 0, endIndex: Int = text.length): List<DictEntry>
    fun match(word: String): DictEntry? = matchPrefix(word)?.takeIf { it.key.length == word.length }
    /** Encoded lookup. Standard dictionaries preserve unmatched non-canonical/truncated bytes. */
    fun matchPrefixUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size): DictEntry? =
        matchPrefix(UTF8Util.decode(input, startIndex, endIndex))
    fun matchAllPrefixesUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size): List<DictEntry> =
        matchAllPrefixes(UTF8Util.decode(input, startIndex, endIndex))
}

enum class DictGroupMatchPolicy { ShortCircuit, Union }

/** Packed UTF-16 trie: hot-path prefix lookups return stored entries and allocate no substrings. */
class TextDict(override val lexicon: Lexicon) : Dict {
    private val entries = lexicon.entries
    override val keyMaxLength: Int = entries.maxOfOrNull { it.key.length } ?: 0
    private val starts: IntArray
    private val counts: IntArray
    private val terminals: IntArray
    private val labels: CharArray
    private val destinations: IntArray

    init {
        // DictEntry has already validated keys while computing their encoded lengths.
        // Sorting indexes changes only construction order, never public lexicon/candidate IDs.
        val ordered = entries.indices.sortedWith { a, b -> entries[a].key.compareTo(entries[b].key) }
        fun sharedPrefix(a: String, b: String): Int {
            var length = 0
            val limit = minOf(a.length, b.length)
            while (length < limit && a[length] == b[length]) length++
            return length
        }
        var nodeCount = 1
        var previous = ""
        for (id in ordered) {
            val key = entries[id].key
            val added = key.length - sharedPrefix(previous, key)
            if (added > Int.MAX_VALUE - nodeCount) throw InvalidFormat("Text dictionary exceeds Kotlin trie capacity")
            nodeCount += added
            previous = key
        }
        val size = nodeCount
        starts = IntArray(size)
        counts = IntArray(size)
        terminals = IntArray(size) { -1 }
        val parents = IntArray(size)
        val nodeLabels = CharArray(size)
        val path = IntArray(keyMaxLength + 1)
        var node = 1
        previous = ""
        for (id in ordered) {
            val key = entries[id].key
            val shared = sharedPrefix(previous, key)
            for (position in shared until key.length) {
                val parent = path[position]
                parents[node] = parent
                nodeLabels[node] = key[position]
                counts[parent]++
                path[position + 1] = node++
            }
            val terminal = path[key.length]
            if (terminals[terminal] >= 0) throw InvalidFormat("The text dictionary contains duplicated keys: $key.")
            terminals[terminal] = id
            previous = key
        }
        labels = CharArray(size - 1)
        destinations = IntArray(labels.size)
        var arc = 0
        for (index in 0 until size) {
            starts[index] = arc
            arc += counts[index]
        }
        val filled = IntArray(size)
        for (child in 1 until size) {
            val parent = parents[child]
            val index = starts[parent] + filled[parent]++
            labels[index] = nodeLabels[child]
            destinations[index] = child
        }
    }

    private fun next(node: Int, char: Char): Int {
        var low = starts[node]
        var high = low + counts[node] - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            when {
                labels[middle] < char -> low = middle + 1
                labels[middle] > char -> high = middle - 1
                else -> return destinations[middle]
            }
        }
        return -1
    }

    override fun matchPrefix(text: String, startIndex: Int, endIndex: Int): DictEntry? {
        UTF8Util.requireRange(text, startIndex, endIndex)
        var node = 0
        var result: DictEntry? = null
        var cursor = startIndex
        while (cursor < endIndex) {
            node = next(node, text[cursor++])
            if (node < 0) break
            if (terminals[node] >= 0) result = entries[terminals[node]]
        }
        return result
    }

    override fun match(word: String): DictEntry? {
        if (word.isEmpty()) return terminals[0].takeIf { it >= 0 }?.let { entries[it] }
        return super.match(word)
    }

    override fun matchAllPrefixes(text: String, startIndex: Int, endIndex: Int): List<DictEntry> {
        UTF8Util.requireRange(text, startIndex, endIndex)
        val result = mutableListOf<DictEntry>()
        var node = 0
        var cursor = startIndex
        while (cursor < endIndex) {
            node = next(node, text[cursor++])
            if (node < 0) break
            if (terminals[node] >= 0) result.add(entries[terminals[node]])
        }
        result.reverse()
        return result
    }

    private inline fun visitEncoded(input: ByteArray, startIndex: Int, endIndex: Int, terminal: (Int) -> Unit) {
        EncodedUTF8.range(input, startIndex, endIndex)
        var node = 0
        var cursor = startIndex
        while (cursor < endIndex) {
            val point = EncodedUTF8.scalar(input, cursor, endIndex)
            if (point < 0) break
            if (point < 0x10000) node = next(node, point.toChar()) else {
                node = next(node, (0xD800 + ((point - 0x10000) ushr 10)).toChar())
                if (node >= 0) node = next(node, (0xDC00 + ((point - 0x10000) and 1023)).toChar())
            }
            if (node < 0) break
            if (terminals[node] >= 0) terminal(terminals[node])
            cursor += EncodedUTF8.width(input, cursor)
        }
    }

    override fun matchPrefixUtf8(input: ByteArray, startIndex: Int, endIndex: Int): DictEntry? {
        var result: DictEntry? = null
        visitEncoded(input, startIndex, endIndex) { result = entries[it] }
        return result
    }
    override fun matchAllPrefixesUtf8(input: ByteArray, startIndex: Int, endIndex: Int): List<DictEntry> {
        val result = mutableListOf<DictEntry>()
        visitEncoded(input, startIndex, endIndex) { result.add(entries[it]) }
        result.reverse()
        return result
    }

    companion object {
        fun fromText(text: String, keyValueDelimiter: Char = '\t'): TextDict = TextDict(Lexicon.parse(text, keyValueDelimiter).sorted())
        fun fromBytes(bytes: ByteArray): TextDict = fromText(UTF8Util.decode(bytes))
        fun fromDict(dict: Dict): TextDict = TextDict(dict.lexicon)
    }
}

open class DictGroup(dicts: List<Dict>, override val matchPolicy: DictGroupMatchPolicy = DictGroupMatchPolicy.ShortCircuit) : Dict {
    private val dictionaries = dicts.toList()
    override val groupItems: List<Dict> get() = dictionaries.toList()
    override val keyMaxLength: Int = dictionaries.maxOfOrNull { it.keyMaxLength } ?: 0
    override val lexicon: Lexicon get() = Lexicon(dictionaries.flatMap { it.lexicon.entries }).sorted()
    override fun match(word: String): DictEntry? = dictionaries.firstNotNullOfOrNull { it.match(word) }
    override fun matchPrefix(text: String, startIndex: Int, endIndex: Int): DictEntry? {
        var result: DictEntry? = null
        for (dict in dictionaries) {
            val entry = dict.matchPrefix(text, startIndex, endIndex) ?: continue
            if (matchPolicy == DictGroupMatchPolicy.ShortCircuit) return entry
            if (result == null || entry.key.length > result.key.length) result = entry
        }
        return result
    }
    override fun matchAllPrefixes(text: String, startIndex: Int, endIndex: Int): List<DictEntry> {
        val matched = mutableMapOf<Int, DictEntry>()
        for (dict in dictionaries) {
            for (entry in dict.matchAllPrefixes(text, startIndex, endIndex)) matched.getOrPut(entry.key.length) { entry }
        }
        return matched.values.sortedByDescending { it.key.length }
    }
    override fun matchPrefixUtf8(input: ByteArray, startIndex: Int, endIndex: Int): DictEntry? {
        EncodedUTF8.range(input, startIndex, endIndex)
        var result: DictEntry? = null
        for (dict in dictionaries) {
            val entry = dict.matchPrefixUtf8(input, startIndex, endIndex) ?: continue
            if (matchPolicy == DictGroupMatchPolicy.ShortCircuit) return entry
            if (result == null || entry.keyByteLength > result.keyByteLength) result = entry
        }
        return result
    }
    override fun matchAllPrefixesUtf8(input: ByteArray, startIndex: Int, endIndex: Int): List<DictEntry> {
        EncodedUTF8.range(input, startIndex, endIndex)
        val matched = mutableMapOf<Int, DictEntry>()
        for (dict in dictionaries) for (entry in dict.matchAllPrefixesUtf8(input, startIndex, endIndex)) {
            matched.getOrPut(entry.keyByteLength) { entry }
        }
        return matched.values.sortedByDescending { it.keyByteLength }
    }
}

class UnionDictGroup(dicts: List<Dict>) : DictGroup(dicts, DictGroupMatchPolicy.Union)
