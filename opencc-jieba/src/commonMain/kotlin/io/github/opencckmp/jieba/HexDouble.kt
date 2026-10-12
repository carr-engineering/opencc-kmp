package io.github.opencckmp.jieba

/** C's hexadecimal floating-point prefix, rounded directly to IEEE-754 binary64. */
internal fun hexDouble(text: String, begin: Int): Double {
    var cursor = begin
    val negative = text.getOrNull(cursor) == '-'
    if (text.getOrNull(cursor) == '+' || negative) cursor++
    cursor += 2 // Caller checked 0x.
    val start = cursor
    var integerDigits = 0
    var digitCount = 0
    var firstIndex = -1
    var firstDigit = 0
    var fractional = false
    while (cursor < text.length) {
        val value = hexDigit(text[cursor])
        if (value >= 0) {
            if (!fractional) integerDigits++
            if (firstIndex < 0 && value != 0) { firstIndex = digitCount; firstDigit = value }
            digitCount++
            cursor++
        } else if (text[cursor] == '.' && !fractional) {
            fractional = true
            cursor++
        } else break
    }
    val end = cursor
    var exponent = 0L
    if (text.getOrNull(cursor) == 'p' || text.getOrNull(cursor) == 'P') {
        cursor++
        val minus = text.getOrNull(cursor) == '-'
        if (minus || text.getOrNull(cursor) == '+') cursor++
        while (cursor < text.length && text[cursor] in '0'..'9') {
            exponent = minOf(1_000_000_000L, exponent * 10 + (text[cursor++] - '0'))
        }
        if (minus) exponent = -exponent
    }
    val sign = if (negative) Long.MIN_VALUE else 0L
    if (firstIndex < 0) return Double.fromBits(sign)
    var highest = 3
    while ((firstDigit and (1 shl highest)) == 0) highest--
    var top = 4L * (integerDigits.toLong() - firstIndex - 1) + highest + exponent
    if (top > 1023) return Double.fromBits(sign or 0x7ff0000000000000L)
    if (top < -1075) return Double.fromBits(sign)
    val quantum = maxOf(-1074L, top - 52)
    var position = top
    var mantissa = 0L
    var round = false
    var sticky = false
    var significant = false
    for (index in start until end) {
        val digit = hexDigit(text[index])
        if (digit < 0) continue
        for (bit in 3 downTo 0) {
            val set = (digit and (1 shl bit)) != 0
            if (!significant) { if (!set) continue; significant = true }
            if (set) {
                when {
                    position >= quantum -> mantissa = mantissa or (1L shl (position - quantum).toInt())
                    position == quantum - 1 -> round = true
                    else -> sticky = true
                }
            }
            position--
        }
    }
    if (round && (sticky || mantissa and 1L != 0L)) mantissa++
    if (top < -1022) return Double.fromBits(sign or mantissa)
    if (mantissa == (1L shl 53)) { mantissa = mantissa ushr 1; top++ }
    if (top > 1023) return Double.fromBits(sign or 0x7ff0000000000000L)
    return Double.fromBits(sign or ((top + 1023) shl 52) or (mantissa and 0xfffffffffffffL))
}

private fun hexDigit(character: Char): Int = when (character) {
    in '0'..'9' -> character - '0'
    in 'a'..'f' -> character - 'a' + 10
    in 'A'..'F' -> character - 'A' + 10
    else -> -1
}
