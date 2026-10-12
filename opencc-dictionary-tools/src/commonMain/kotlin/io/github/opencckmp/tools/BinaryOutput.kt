package io.github.opencckmp.tools

import io.github.opencckmp.DictionaryByteOrder
import io.github.opencckmp.InvalidFormat

internal class BinaryOutput(private val order: DictionaryByteOrder = DictionaryByteOrder.LittleEndian) {
    fun sibling(): BinaryOutput = BinaryOutput(order)
    private var bytes = ByteArray(256)
    var size: Int = 0
        private set
    private fun reserve(count: Int) {
        val required = size.toLong() + count
        if (count < 0 || required > Int.MAX_VALUE) throw InvalidFormat("Dictionary exceeds Kotlin byte-array capacity")
        if (required > bytes.size) bytes = bytes.copyOf(maxOf(required.toInt(), minOf(bytes.size.toLong() * 2, Int.MAX_VALUE.toLong()).toInt()))
    }
    fun append(data: ByteArray) { reserve(data.size); data.copyInto(bytes, size); size += data.size }
    fun byte(value: Int) { reserve(1); bytes[size++] = value.toByte() }
    fun integer(value: Long, width: Int) {
        require(width == 2 || width == 4 || width == 8)
        if (width < 8 && (value < 0 || value ushr (width * 8) != 0L)) throw InvalidFormat("Dictionary integer exceeds $width-byte field")
        reserve(width)
        repeat(width) { index ->
            val shift = if (order == DictionaryByteOrder.LittleEndian) index * 8 else (width - 1 - index) * 8
            bytes[size++] = (value ushr shift).toByte()
        }
    }
    fun word32(value: Int) = integer(value.toLong() and 0xFFFFFFFFL, 4)
    fun vector(data: ByteArray) {
        integer(data.size.toLong(), 8)
        append(data)
        repeat((8 - data.size % 8) % 8) { byte(0) }
    }
    fun toByteArray(): ByteArray = bytes.copyOf(size)
}
