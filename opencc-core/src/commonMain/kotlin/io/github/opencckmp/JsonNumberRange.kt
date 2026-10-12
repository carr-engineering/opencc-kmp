/* Adapted from pinned RapidJSON reader.h/strtod.h/pow10.h. MIT; see licenses/rapidjson.txt. */
package io.github.opencckmp

/** Range acceptance for already lexically validated JSON numbers; retains the original token. */
internal object JsonNumberRange {
    fun accepts(text: String): Boolean {
        val minus = text[0] == '-'
        var cursor = if (minus) 1 else 0
        val integerThreshold = if (minus) 922337203685477580uL else 1844674407370955161uL
        val lastIntegerDigit = if (minus) 8uL else 5uL
        var integer = 0uL
        var decimal = 0.0
        var useDouble = false
        var significantDigits = 0
        var first = true
        while (cursor < text.length && text[cursor] in '0'..'9') {
            val digit = (text[cursor++] - '0').toULong()
            if (useDouble) decimal = decimal * 10.0 + digit.toDouble()
            else if (integer > integerThreshold || (integer == integerThreshold && digit > lastIntegerDigit)) {
                decimal = integer.toDouble() * 10.0 + digit.toDouble()
                useDouble = true
            } else {
                integer = integer * 10uL + digit
                if (!first) significantDigits++
            }
            first = false
        }
        var fractionExponent = 0
        if (cursor < text.length && text[cursor] == '.') {
            cursor++
            if (!useDouble) {
                while (cursor < text.length && text[cursor] in '0'..'9' && integer <= 9007199254740991uL) {
                    integer = integer * 10uL + (text[cursor++] - '0').toULong()
                    fractionExponent--
                    if (integer != 0uL) significantDigits++
                }
                decimal = integer.toDouble()
                useDouble = true
            }
            while (cursor < text.length && text[cursor] in '0'..'9') {
                val digit = text[cursor++] - '0'
                if (significantDigits < 17) {
                    decimal = decimal * 10.0 + digit
                    fractionExponent--
                    if (decimal > 0.0) significantDigits++
                }
            }
        }
        var exponent = 0L
        if (cursor < text.length) {
            cursor++ // e/E; grammar validation precedes this check.
            if (!useDouble) { decimal = integer.toDouble(); useDouble = true }
            val negativeExponent = text[cursor] == '-'
            if (text[cursor] == '-' || text[cursor] == '+') cursor++
            val limit = if (negativeExponent) (fractionExponent.toLong() + 2147483639L) / 10L
                        else 308L - fractionExponent
            while (cursor < text.length) {
                exponent = exponent * 10L + (text[cursor++] - '0')
                if (exponent > limit) {
                    if (!negativeExponent) return false
                    break // Native consumes the remaining digits without changing this exponent.
                }
            }
            if (negativeExponent) exponent = -exponent
        }
        if (!useDouble) return true
        val power = exponent + fractionExponent
        decimal = if (power < -308L) scale(scale(decimal, -308L), power + 308L) else scale(decimal, power)
        return decimal <= Double.MAX_VALUE
    }

    private fun scale(value: Double, power: Long): Double = when {
        power < -308L -> 0.0
        power >= 0L -> value * powerOfTen(power.toInt())
        else -> value / powerOfTen((-power).toInt())
    }

    // A coarse table times a small power differs from the rounded decimal literal by at most
    // one ULP. Corrections retain every bit of the pinned 309-constant table using 36 doubles.
    internal fun powerOfTen(power: Int): Double {
        val product = powers[power / 16] * powers[20 + power % 16]
        return Double.fromBits(product.toBits() + (corrections[power].code - '2'.code))
    }
    private const val corrections = "222222222222222222222222222222222112212122222122212222222211112222221222221222222222222222222222222122122122222122322322232222222221121122222222222222222222221222222122222222222222222122212222221221222212222222222222223222232223223332233232222222222212221222212222222222222111221222212122222222222222222222322"
    private val powers = doubleArrayOf(
        1e0, 1e16, 1e32, 1e48, 1e64, 1e80, 1e96, 1e112, 1e128, 1e144, 1e160, 1e176,
        1e192, 1e208, 1e224, 1e240, 1e256, 1e272, 1e288, 1e304, 1e0, 1e1, 1e2, 1e3,
        1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14, 1e15
    )
}
