/* Adapted from MARISA, Copyright (c) 2010-2025 Susumu Yata, BSD-2-Clause. */
package io.github.opencckmp.tools

/** Native 32/64-bit packed-word layout with upstream-compatible rank/select acceleration tables. */
internal class MarisaBits {
    private val bits = mutableListOf<Boolean>()
    val size: Int get() = bits.size
    operator fun get(index: Int): Boolean = bits[index]
    fun add(value: Boolean) { bits.add(value) }
    fun rank1(index: Int): Int = (0 until index).count { bits[it] }

    fun write(output: BinaryOutput, built: Boolean = true, select0: Boolean = false, select1: Boolean = false) {
        val words = LongArray((bits.size + 63) / 64)
        for (index in bits.indices) if (bits[index]) words[index / 64] = words[index / 64] or (1L shl (index % 64))
        val data = output.sibling()
        for (word in words) data.packedWord(word)
        output.vector(data.toByteArray())
        output.integer(bits.size.toLong(), 4)
        output.integer(words.sumOf { it.countOneBits() }.toLong(), 4)
        val ranks = output.sibling()
        val zeros = output.sibling()
        val ones = output.sibling()
        if (built) {
            val absolute = IntArray((bits.size + 511) / 512 + 1)
            val low = IntArray(absolute.size)
            val high = IntArray(absolute.size)
            fun relative(group: Int, part: Int, value: Int) {
                when (part) {
                    1 -> low[group] = (low[group] and 127.inv()) or value
                    2 -> low[group] = (low[group] and (255 shl 7).inv()) or (value shl 7)
                    3 -> low[group] = (low[group] and (255 shl 15).inv()) or (value shl 15)
                    4 -> low[group] = (low[group] and (511 shl 23).inv()) or (value shl 23)
                    5 -> high[group] = (high[group] and 511.inv()) or value
                    6 -> high[group] = (high[group] and (511 shl 9).inv()) or (value shl 9)
                    7 -> high[group] = (high[group] and (511 shl 18).inv()) or (value shl 18)
                }
            }
            var count = 0
            for (word in words.indices) {
                val group = word / 8
                if (word % 8 == 0) absolute[group] = count else relative(group, word % 8, count - absolute[group])
                count += words[word].countOneBits()
            }
            if (bits.size % 512 != 0) {
                val group = (bits.size - 1) / 512
                for (part in ((bits.size - 1) / 64 % 8) + 1..7) relative(group, part, count - absolute[group])
            }
            absolute[absolute.lastIndex] = count
            for (group in absolute.indices) {
                ranks.word32(absolute[group]); ranks.word32(low[group]); ranks.word32(high[group])
            }
            var zeroCount = 0
            var oneCount = 0
            for (index in bits.indices) {
                if (bits[index]) {
                    if (select1 && oneCount % 512 == 0) ones.integer(index.toLong(), 4)
                    oneCount++
                } else {
                    if (select0 && zeroCount % 512 == 0) zeros.integer(index.toLong(), 4)
                    zeroCount++
                }
            }
            if (select0) zeros.integer(bits.size.toLong(), 4)
            if (select1) ones.integer(bits.size.toLong(), 4)
        }
        output.vector(ranks.toByteArray())
        output.vector(zeros.toByteArray())
        output.vector(ones.toByteArray())
    }
}

internal fun writeMarisaFlat(output: BinaryOutput, values: IntArray) {
    val maximum = values.maxOrNull() ?: 0
    val width = if (maximum == 0) 0 else 32 - maximum.countLeadingZeroBits()
    val words = LongArray(if (values.isEmpty()) 0 else maxOf(1, ((values.size.toLong() * width + 63) / 64).toInt()))
    for (index in values.indices) {
        if (width == 0) break
        val position = index.toLong() * width
        val unit = (position / 64).toInt()
        val offset = (position % 64).toInt()
        words[unit] = words[unit] or (values[index].toLong() shl offset)
        if (offset + width > 64) words[unit + 1] = words[unit + 1] or (values[index].toLong() ushr (64 - offset))
    }
    val data = output.sibling()
    for (word in words) data.packedWord(word)
    output.vector(data.toByteArray())
    output.integer(width.toLong(), 4)
    output.integer((1L shl width) - 1, 4)
    output.integer(values.size.toLong(), 8)
}
