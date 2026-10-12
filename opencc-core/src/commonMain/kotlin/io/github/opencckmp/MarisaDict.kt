/*
 * OpenCC container: Copyright 2010-2026 Carbo Kuo and contributors, Apache-2.0.
 * MARISA layout/restoration adapted from MARISA (Copyright 2010-2025 Susumu Yata), BSD-2-Clause.
 * See licenses/marisa.txt. No native MARISA library is linked or invoked by this reader.
 */
package io.github.opencckmp

/** Reads upstream MARISA LOUDS images, restoring ordered key IDs into a packed Kotlin lookup trie. */
class MarisaDict private constructor(private val dictionary: TextDict) : Dict by dictionary {
    companion object {
        /** MARISA stores native-endian 32- or 64-bit packed words; little-endian layouts coincide. */
        fun fromBytes(bytes: ByteArray, byteOrder: DictionaryByteOrder? = null, wordSize: Int? = null): MarisaDict {
            require(wordSize == null || wordSize == 32 || wordSize == 64) { "MARISA wordSize must be 32 or 64" }
            var first: InvalidFormat? = null
            for (order in byteOrder?.let { listOf(it) } ?: DictionaryByteOrder.entries) {
                for (width in wordSize?.let { listOf(it) } ?: listOf(64, 32)) {
                    try { return read(BinaryCursor(bytes, order), width) }
                    catch (error: InvalidFormat) { if (first == null) first = error }
                }
            }
            throw first!!
        }

        private fun read(cursor: BinaryCursor, wordSize: Int): MarisaDict {
            cursor.expect("OPENCC_MARISA_0.2.5")
            cursor.expect("We love Marisa.\u0000")
            val trie = LoudsImage.read(cursor, 0, wordSize)
            val values = SerializedValues.read(cursor)
            if (values.size != trie.terminals.ones.size) throw InvalidFormat("Marisa dictionary key count mismatch")
            val entries = List(values.size) { id ->
                val key = UTF8Util.decode(trie.key(trie.terminals.ones[id]))
                DictEntry(key, values[id])
            }
            return MarisaDict(TextDict(Lexicon(entries)))
        }
    }
}

private fun packedWords(data: ByteArray, order: DictionaryByteOrder, wordSize: Int): LongArray {
    val reader = BinaryCursor(data, order)
    return LongArray(data.size / 8) {
        if (wordSize == 64) reader.integer(8) else {
            val low = reader.integer(4)
            low or (reader.integer(4) shl 32)
        }
    }
}

object MarisaDictionaryReader : DictionaryReader {
    override val type: String = "ocd2"
    override fun read(bytes: ByteArray): Dict = MarisaDict.fromBytes(bytes)
}

internal class PackedBits(private val words: LongArray, val size: Int, val ones: IntArray) {
    private val ranks = IntArray(words.size + 1).also { counts ->
        for (i in words.indices) counts[i + 1] = counts[i] + words[i].countOneBits()
    }
    operator fun get(index: Int): Boolean {
        if (index !in 0 until size) throw InvalidFormat("Marisa bit index out of bounds")
        return words[index ushr 6] and (1L shl (index and 63)) != 0L
    }
    fun rank1(index: Int): Int {
        if (index !in 0..size) throw InvalidFormat("Marisa rank index out of bounds")
        val word = index ushr 6
        val bit = index and 63
        return ranks[word] + if (bit == 0) 0 else (words[word] and ((1L shl bit) - 1)).countOneBits()
    }
    companion object {
        fun read(cursor: BinaryCursor, wordSize: Int): PackedBits {
            val data = cursor.vector()
            if (data.size % 8 != 0) throw InvalidFormat("Marisa bit-vector alignment")
            val size = cursor.count(4, data.size * 8)
            val count = cursor.count(4, size)
            // Native rank/select accelerators are serialized vectors; reconstruct portable indexes.
            cursor.vector()
            cursor.vector()
            cursor.vector()
            val words = packedWords(data, cursor.order, wordSize)
            val positions = IntArray(count)
            var found = 0
            for (wordIndex in words.indices) {
                var word = words[wordIndex]
                while (word != 0L) {
                    val position = wordIndex * 64 + word.countTrailingZeroBits()
                    if (position < size) {
                        if (found >= positions.size) throw InvalidFormat("Marisa bit-vector population mismatch")
                        positions[found++] = position
                    }
                    word = word and (word - 1)
                }
            }
            if (found != count) throw InvalidFormat("Marisa bit-vector population mismatch")
            return PackedBits(words, size, positions)
        }
    }
}

internal class FlatValues(private val words: LongArray, private val width: Int, val size: Int) {
    fun value(index: Int): Long {
        if (index !in 0 until size) throw InvalidFormat("Marisa packed index out of bounds")
        if (width == 0) return 0
        val bit = index.toLong() * width
        val word = (bit ushr 6).toInt()
        val offset = (bit and 63).toInt()
        var value = words[word] ushr offset
        if (offset + width > 64) value = value or (words[word + 1] shl (64 - offset))
        return value and ((1L shl width) - 1)
    }
    companion object {
        fun read(cursor: BinaryCursor, wordSize: Int): FlatValues {
            val data = cursor.vector()
            if (data.size % 8 != 0) throw InvalidFormat("Marisa packed-vector alignment")
            val width = cursor.count(4, 32)
            val mask = cursor.integer(4)
            if (mask != (1L shl width) - 1) throw InvalidFormat("Marisa packed-vector mask mismatch")
            val size = cursor.count(8)
            if (width.toLong() * size > data.size.toLong() * 8) throw InvalidFormat("Marisa packed-vector size mismatch")
            return FlatValues(packedWords(data, cursor.order, wordSize), width, size)
        }
    }
}

internal class ByteAccumulator {
    private var bytes = ByteArray(32)
    var size: Int = 0
        private set
    fun add(byte: Byte) {
        if (size == bytes.size) bytes = bytes.copyOf(bytes.size * 2)
        bytes[size++] = byte
    }
    fun append(data: ByteArray, reverse: Boolean = false) {
        if (reverse) for (index in data.indices.reversed()) add(data[index])
        else for (byte in data) add(byte)
    }
    fun toByteArray(): ByteArray = bytes.copyOf(size)
}

internal class LoudsImage(
    private val louds: PackedBits,
    val terminals: PackedBits,
    private val links: PackedBits,
    private val bases: ByteArray,
    private val extras: FlatValues,
    private val tail: ByteArray,
    private val tailEnds: PackedBits,
    private val next: LoudsImage?,
    private val firstLevelNodes: Int,
) {
    private fun parent(node: Int): Int {
        if (node == 0) return 0
        val parent = louds.ones.getOrNull(node)?.minus(node + 1) ?: throw InvalidFormat("Marisa node out of bounds")
        if (parent !in 0 until node) throw InvalidFormat("Invalid Marisa parent link")
        return parent
    }
    private fun edge(node: Int): ByteArray {
        if (node !in bases.indices) throw InvalidFormat("Marisa node out of bounds")
        if (!links[node]) return byteArrayOf(bases[node])
        val link = (bases[node].toLong() and 255) + extras.value(links.rank1(node)) * 256
        if (link > Int.MAX_VALUE) throw InvalidFormat("Marisa link out of bounds")
        if (next != null) return next.restore(link.toInt())
        val offset = link.toInt()
        if (offset !in tail.indices) throw InvalidFormat("Marisa tail offset out of bounds")
        var end = offset
        if (tailEnds.size == 0) {
            while (end < tail.size && tail[end] != 0.toByte()) end++
            if (end == tail.size) throw InvalidFormat("Marisa tail is not terminated")
        } else {
            while (end < tail.size && !tailEnds[end]) end++
            if (end == tail.size) throw InvalidFormat("Marisa binary tail is not terminated")
            end++
        }
        return tail.copyOfRange(offset, end)
    }

    /** Top-level path: gather edges upwards, reversing each edge and then the full result. */
    fun key(start: Int): ByteArray {
        val result = ByteAccumulator()
        var node = start
        while (node > 0) {
            result.append(edge(node), reverse = true)
            node = parent(node)
        }
        return result.toByteArray().also { it.reverse() }
    }

    /** Nested tries store reverse keys; walking leaf → root produces the original linked edge. */
    private fun restore(start: Int): ByteArray {
        if (start !in 1 until bases.size) throw InvalidFormat("Marisa nested link out of bounds")
        val result = ByteAccumulator()
        var node = start
        while (node > 0) {
            result.append(edge(node))
            node = parent(node)
        }
        return result.toByteArray()
    }

    companion object {
        fun read(cursor: BinaryCursor, depth: Int, wordSize: Int): LoudsImage {
            if (depth > 127) throw InvalidFormat("Marisa trie nesting limit")
            val louds = PackedBits.read(cursor, wordSize)
            val terminals = PackedBits.read(cursor, wordSize)
            val links = PackedBits.read(cursor, wordSize)
            val bases = cursor.vector()
            val extras = FlatValues.read(cursor, wordSize)
            val tail = cursor.vector()
            val tailEnds = PackedBits.read(cursor, wordSize)
            if (bases.isEmpty() || louds.ones.size != bases.size || links.size != bases.size || extras.size != links.ones.size) {
                throw InvalidFormat("Marisa node/vector counts mismatch")
            }
            val next = if (links.ones.isNotEmpty() && tail.isEmpty()) read(cursor, depth + 1, wordSize) else null
            val cache = cursor.vector()
            if (cache.size == 0 || cache.size % 12 != 0) throw InvalidFormat("Marisa cache size/alignment")
            val firstLevel = cursor.count(4, bases.size)
            cursor.integer(4) // Config flags affect construction/search order, already encoded in nodes.
            if (tailEnds.size != 0 && tailEnds.size != tail.size) throw InvalidFormat("Marisa tail flag count mismatch")
            if (louds.ones.firstOrNull() != 0 || links[0]) throw InvalidFormat("Invalid Marisa root")
            return LoudsImage(louds, terminals, links, bases, extras, tail, tailEnds, next, firstLevel)
        }
    }
}
