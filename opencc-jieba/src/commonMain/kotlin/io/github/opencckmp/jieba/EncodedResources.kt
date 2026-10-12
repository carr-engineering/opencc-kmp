/* Adapted from cppjieba (MIT); see licenses/CPPJIEBA-LICENSE. */
package io.github.opencckmp.jieba

/** Iterate getline-style ranges without retaining copies of dictionary lines. */
internal inline fun forEachResourceLine(bytes: ByteArray, action: (Int, Int) -> Unit) {
    var start = 0
    while (start < bytes.size) {
        var end = start
        while (end < bytes.size && bytes[end] != 10.toByte()) end++
        action(start, end)
        start = end + 1
    }
}

/** cppjieba Split retains interior empty fields and omits the final empty field. */
internal fun resourceFields(bytes: ByteArray, start: Int, end: Int, separator: Byte): IntArray {
    if (start == end) return IntArray(0)
    var count = 1
    for (index in start until end) if (bytes[index] == separator) count++
    if (bytes[end - 1] == separator) count--
    val fields = IntArray(count * 2)
    var from = start
    var field = 0
    while (from < end) {
        var until = from
        while (until < end && bytes[until] != separator) until++
        fields[field++] = from
        fields[field++] = until
        from = until + 1
    }
    return fields
}

/** Exact cppjieba byte/rune acceptance, without imposing Unicode scalar validity on encoded resources. */
internal fun decodeResourceRunes(bytes: ByteArray, start: Int, end: Int): IntArray? {
    fun width(index: Int): Int {
        val first = bytes[index].toInt() and 255
        return when {
            first < 128 -> 1
            first <= 0xdf && index + 1 < end -> 2
            first <= 0xef && index + 2 < end -> 3
            first <= 0xf7 && index + 3 < end -> 4
            else -> 0
        }
    }
    var cursor = start
    var count = 0
    while (cursor < end) {
        val size = width(cursor)
        if (size == 0) return null
        count++
        cursor += size
    }
    val points = IntArray(count)
    cursor = start
    var index = 0
    while (cursor < end) {
        val size = width(cursor)
        var point = (bytes[cursor].toInt() and 255) and when (size) { 1 -> 127; 2 -> 31; 3 -> 15; else -> 7 }
        for (offset in 1 until size) point = (point shl 6) or (bytes[cursor + offset].toInt() and 63)
        points[index++] = point
        cursor += size
    }
    return points
}
