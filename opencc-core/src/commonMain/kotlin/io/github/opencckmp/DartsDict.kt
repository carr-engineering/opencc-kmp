/* Adapted from OpenCC. Copyright 2010-2026 Carbo Kuo and contributors. Apache-2.0. */
package io.github.opencckmp

/** Reads fixed uint32 and legacy uint64 Darts images. Runtime traverses the double-array directly. */
class DartsDict private constructor(private val units: IntArray, override val lexicon: Lexicon) : Dict {
    private val entries = lexicon.entries
    override val keyMaxLength: Int = entries.maxOfOrNull { it.key.length } ?: 0
    private fun label(unit: Int): Int = unit and (Int.MIN_VALUE or 255)
    private fun offset(unit: Int): Int = (unit ushr 10) shl ((unit and 512) ushr 6)
    private fun leaf(unit: Int): Boolean = unit and 256 != 0
    private fun value(node: Int): DictEntry {
        val index = units[node xor offset(units[node])] and Int.MAX_VALUE
        return entries[index]
    }

    init {
        if (units.isEmpty() || label(units[0]) != 0 || leaf(units[0]) || offset(units[0]) == 0) throw InvalidFormat("Invalid Darts root")
        if ((offset(units[0]) or 255) >= units.size) throw InvalidFormat("Darts root offset out of bounds")
        for (node in 1 until units.size) {
            if (label(units[node]) in 0..255) {
                if (((node xor offset(units[node])) or 255) >= units.size) throw InvalidFormat("Darts offset out of bounds")
            } else if ((units[node] and Int.MAX_VALUE) >= entries.size) throw InvalidFormat("Darts value out of bounds")
        }
    }

    override fun matchPrefix(text: String, startIndex: Int, endIndex: Int): DictEntry? {
        UTF8Util.requireRange(text, startIndex, endIndex)
        var node = 0
        var result: DictEntry? = null
        visitUtf8(text, startIndex, endIndex) { byte ->
            node = node xor offset(units[node]) xor byte
            if (label(units[node]) != byte) false
            else { if (leaf(units[node])) result = value(node); true }
        }
        return result
    }
    override fun match(word: String): DictEntry? {
        var node = 0
        val matched = visitUtf8(word, 0, word.length) { byte ->
            node = node xor offset(units[node]) xor byte
            label(units[node]) == byte
        }
        return if (matched && leaf(units[node])) value(node) else null
    }
    override fun matchAllPrefixes(text: String, startIndex: Int, endIndex: Int): List<DictEntry> {
        UTF8Util.requireRange(text, startIndex, endIndex)
        var node = 0
        val result = mutableListOf<DictEntry>()
        visitUtf8(text, startIndex, endIndex) { byte ->
            node = node xor offset(units[node]) xor byte
            if (label(units[node]) != byte) false
            else { if (leaf(units[node])) result.add(value(node)); true }
        }
        result.reverse()
        return result
    }

    private inline fun visitEncoded(input: ByteArray, startIndex: Int, endIndex: Int, terminal: (DictEntry) -> Unit) {
        EncodedUTF8.range(input, startIndex, endIndex)
        var node = 0
        for (cursor in startIndex until endIndex) {
            val byte = input[cursor].toInt() and 255
            node = node xor offset(units[node]) xor byte
            if (label(units[node]) != byte) break
            if (leaf(units[node])) terminal(value(node))
        }
    }
    override fun matchPrefixUtf8(input: ByteArray, startIndex: Int, endIndex: Int): DictEntry? {
        var result: DictEntry? = null
        visitEncoded(input, startIndex, endIndex) { result = it }
        return result
    }
    override fun matchAllPrefixesUtf8(input: ByteArray, startIndex: Int, endIndex: Int): List<DictEntry> {
        val result = mutableListOf<DictEntry>()
        visitEncoded(input, startIndex, endIndex) { result.add(it) }
        result.reverse()
        return result
    }

    companion object {
        fun fromBytes(bytes: ByteArray, byteOrder: DictionaryByteOrder? = null): DartsDict {
            if (byteOrder != null) return read(BinaryCursor(bytes, byteOrder))
            return try { read(BinaryCursor(bytes, DictionaryByteOrder.LittleEndian)) }
            catch (little: InvalidFormat) {
                try { read(BinaryCursor(bytes, DictionaryByteOrder.BigEndian)) }
                catch (_: InvalidFormat) { throw little }
            }
        }
        private fun read(cursor: BinaryCursor): DartsDict {
            cursor.expect("OPENCCDARTS1")
            val probe = BinaryCursor(cursor.bytes, cursor.order, cursor.position)
            val legacy = probe.remaining >= 8 && probe.integer(8) in 1..cursor.remaining.toLong()
            val width = if (legacy) 8 else 4
            val size = cursor.count(width)
            if (size == 0 || size % width != 0 || size > cursor.remaining) throw InvalidFormat("Invalid Darts image size/alignment")
            val units = IntArray(size / width) {
                val unit = cursor.integer(width)
                if (width == 8 && unit ushr 32 != 0L) throw InvalidFormat("Invalid legacy Darts unit")
                unit.toInt()
            }
            val binary = BinaryDict.read(cursor)
            return DartsDict(units, binary.lexicon)
        }
    }
}

object DartsDictionaryReader : DictionaryReader {
    override val type: String = "ocd"
    override fun read(bytes: ByteArray): Dict = DartsDict.fromBytes(bytes)
}
