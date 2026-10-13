package io.github.opencckmp

enum class DictionaryByteOrder { LittleEndian, BigEndian }

internal class BinaryCursor(val bytes: ByteArray, val order: DictionaryByteOrder, var position: Int = 0) {
    val remaining: Int get() = bytes.size - position
    fun skip(count: Int) {
        if (count < 0 || count > remaining) throw InvalidFormat("Truncated binary dictionary")
        position += count
    }
    fun take(count: Int): ByteArray {
        val start = position
        skip(count)
        return bytes.copyOfRange(start, position)
    }
    fun integer(width: Int): Long {
        if ((width != 2 && width != 4 && width != 8) || remaining < width) throw InvalidFormat("Truncated binary dictionary integer")
        var value = 0L
        repeat(width) { index ->
            val shift = if (order == DictionaryByteOrder.LittleEndian) index * 8 else (width - index - 1) * 8
            value = value or ((bytes[position + index].toLong() and 255) shl shift)
        }
        position += width
        return value
    }
    fun count(width: Int = 4, maximum: Int = bytes.size): Int {
        val value = integer(width)
        if (value < 0 || value > maximum) throw InvalidFormat("Binary dictionary count/offset exceeds bounds")
        return value.toInt()
    }
    fun vector(): ByteArray {
        val count = count(8, remaining)
        val result = take(count)
        skip((8 - count % 8) % 8)
        return result
    }
    fun expect(header: String) {
        val expected = header.encodeToByteArray()
        if (remaining < expected.size || !take(expected.size).contentEquals(expected)) throw InvalidFormat("Invalid OpenCC dictionary header")
    }
}

internal fun nullTerminatedString(bytes: ByteArray, start: Int): String {
    if (start !in bytes.indices) throw InvalidFormat("Dictionary string offset out of bounds")
    var end = start
    while (end < bytes.size && bytes[end] != 0.toByte()) end++
    if (end == bytes.size) throw InvalidFormat("Dictionary string is not null-terminated")
    return UTF8Util.decode(bytes, start, end)
}

/** The value section of an OpenCC marisa dictionary, indexed by marisa key ID. */
class SerializedValues private constructor(private val stored: List<List<String>>, val bytesRead: Int) {
    val values: List<List<String>> get() = stored.map { it.toList() }
    val size: Int get() = stored.size
    operator fun get(index: Int): List<String> = stored[index].toList()
    companion object {
        fun fromBytes(bytes: ByteArray, byteOrder: DictionaryByteOrder = DictionaryByteOrder.LittleEndian): SerializedValues =
            read(BinaryCursor(bytes, byteOrder))

        internal fun read(cursor: BinaryCursor): SerializedValues {
            val start = cursor.position
            val items = cursor.count()
            val data = cursor.take(cursor.count())
            var offset = 0
            val values = List(items) {
                val count = cursor.count(2)
                List(count) {
                    val length = cursor.count(2, data.size)
                    if (length == 0 || length > data.size - offset || data[offset + length - 1] != 0.toByte()) {
                        throw InvalidFormat("Invalid dictionary value length/terminator")
                    }
                    val value = nullTerminatedString(data, offset)
                    offset += length
                    value
                }
            }
            return SerializedValues(values, cursor.position - start)
        }
    }
}

/** Legacy/current BinaryDict payload used by `.ocd`; retains ordered entries and candidates. */
class BinaryDict private constructor(override val lexicon: Lexicon) : Dict by TextDict(lexicon) {
    companion object {
        fun fromBytes(bytes: ByteArray, byteOrder: DictionaryByteOrder = DictionaryByteOrder.LittleEndian): BinaryDict =
            read(BinaryCursor(bytes, byteOrder))

        internal fun read(cursor: BinaryCursor): BinaryDict {
            val probe = BinaryCursor(cursor.bytes, cursor.order, cursor.position)
            val legacy = probe.remaining >= 8 && probe.integer(8) in 0..cursor.remaining.toLong() && cursor.remaining != 12
            val width = if (legacy) 8 else 4
            val count = cursor.count(width)
            val keys = cursor.take(cursor.count(width))
            val values = cursor.take(cursor.count(width))
            val entries = List(count) {
                val size = cursor.count(width)
                val key = nullTerminatedString(keys, cursor.count(width, keys.size))
                val candidates = List(size) { nullTerminatedString(values, cursor.count(width, values.size)) }
                DictEntry(key, candidates)
            }
            return BinaryDict(Lexicon(entries))
        }
    }
}

/** Emit UTF-8 octets directly from a valid String without allocating a byte array per lookup. */
internal inline fun visitUtf8(text: String, start: Int, end: Int, visitor: (Int) -> Boolean): Boolean {
    var cursor = start
    while (cursor < end) {
        val point = UTF8Util.codePoint(text, cursor)
        when {
            point < 0x80 -> if (!visitor(point)) return false
            point < 0x800 -> {
                if (!visitor(0xC0 or (point ushr 6)) || !visitor(0x80 or (point and 63))) return false
            }
            point < 0x10000 -> {
                if (!visitor(0xE0 or (point ushr 12)) || !visitor(0x80 or ((point ushr 6) and 63)) || !visitor(0x80 or (point and 63))) return false
            }
            else -> {
                if (!visitor(0xF0 or (point ushr 18)) || !visitor(0x80 or ((point ushr 12) and 63)) ||
                    !visitor(0x80 or ((point ushr 6) and 63)) || !visitor(0x80 or (point and 63))) return false
            }
        }
        cursor = UTF8Util.nextIndex(text, cursor)
    }
    return true
}
