package io.github.opencckmp

/** A key with ordered conversion candidates. The first candidate is the default. */
class DictEntry(val key: String, values: List<String>) {
    private val storedValues = values.toList()
    val values: List<String> get() = storedValues.toList()
    val defaultValue: String = storedValues.firstOrNull() ?: throw InvalidFormat("Dictionary entry has no values")
    val keyLength: Int get() = key.length
    val keyByteLength: Int get() = UTF8Util.encode(key).size
    val numValues: Int get() = storedValues.size
    override fun toString(): String = key + "\t" + storedValues.joinToString(" ")
}

class Lexicon(entries: List<DictEntry>) : Iterable<DictEntry> {
    private val storedEntries = entries.toList()
    val entries: List<DictEntry> get() = storedEntries.toList()
    val size: Int get() = storedEntries.size
    operator fun get(index: Int): DictEntry = storedEntries[index]
    override fun iterator(): Iterator<DictEntry> = storedEntries.iterator()
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

interface Dict {
    val keyMaxLength: Int
    val lexicon: Lexicon
    val groupItems: List<Dict>? get() = null
    val matchPolicy: DictGroupMatchPolicy get() = DictGroupMatchPolicy.ShortCircuit
    fun matchPrefix(text: String, startIndex: Int = 0, endIndex: Int = text.length): DictEntry?
    fun matchAllPrefixes(text: String, startIndex: Int = 0, endIndex: Int = text.length): List<DictEntry>
    fun match(word: String): DictEntry? = matchPrefix(word)?.takeIf { it.key.length == word.length }
}

enum class DictGroupMatchPolicy { ShortCircuit, Union }

/** Packed UTF-16 trie: hot-path prefix lookups return stored entries and allocate no substrings. */
class TextDict(override val lexicon: Lexicon) : Dict {
    private class BuildNode {
        val children = mutableMapOf<Char, Int>()
        var entry = -1
    }
    private val entries = lexicon.entries
    override val keyMaxLength: Int = entries.maxOfOrNull { it.key.length } ?: 0
    private val starts: IntArray
    private val counts: IntArray
    private val terminals: IntArray
    private val labels: CharArray
    private val destinations: IntArray

    init {
        val nodes = mutableListOf(BuildNode())
        entries.forEachIndexed { id, entry ->
            UTF8Util.validate(entry.key)
            var node = 0
            for (char in entry.key) {
                node = nodes[node].children.getOrPut(char) { nodes.add(BuildNode()); nodes.lastIndex }
            }
            if (nodes[node].entry != -1) throw InvalidFormat("The text dictionary contains duplicated keys: ${entry.key}.")
            nodes[node].entry = id
        }
        starts = IntArray(nodes.size)
        counts = IntArray(nodes.size)
        terminals = IntArray(nodes.size)
        labels = CharArray(nodes.size - 1)
        destinations = IntArray(labels.size)
        var arc = 0
        nodes.forEachIndexed { index, node ->
            starts[index] = arc
            counts[index] = node.children.size
            terminals[index] = node.entry
            for ((label, destination) in node.children.entries.sortedBy { it.key }) {
                labels[arc] = label
                destinations[arc++] = destination
            }
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
}

class UnionDictGroup(dicts: List<Dict>) : DictGroup(dicts, DictGroupMatchPolicy.Union)
