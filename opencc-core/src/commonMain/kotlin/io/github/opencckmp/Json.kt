package io.github.opencckmp

internal sealed interface JsonValue {
    class Object(val members: List<Pair<String, JsonValue>>) : JsonValue {
        operator fun get(name: String): JsonValue? = members.firstOrNull { it.first == name }?.second
    }
    class Array(val values: List<JsonValue>) : JsonValue
    class StringValue(val value: String) : JsonValue
    class BooleanValue(val value: Boolean) : JsonValue
    class Number(val value: String) : JsonValue
    object Null : JsonValue
}

/** Small common parser accepting upstream RapidJSON's comments and trailing commas. */
internal class JsonParser(private val input: String) {
    private var cursor = 0
    fun parse(): JsonValue {
        val value = value()
        whitespace()
        if (cursor != input.length) fail("Trailing input")
        return value
    }
    private fun fail(message: String): Nothing = throw InvalidFormat("Error parsing JSON at $cursor: $message")
    private fun whitespace() {
        while (cursor < input.length) {
            when (input[cursor]) {
                ' ', '\t', '\n', '\r' -> cursor++
                '/' -> {
                    when (input.getOrNull(cursor + 1)) {
                        '/' -> { cursor += 2; while (cursor < input.length && input[cursor] != '\n' && input[cursor] != '\r') cursor++ }
                        '*' -> {
                            val end = input.indexOf("*/", cursor + 2)
                            if (end < 0) fail("Unterminated comment")
                            cursor = end + 2
                        }
                        else -> return
                    }
                }
                else -> return
            }
        }
    }
    private fun take(char: Char): Boolean {
        whitespace()
        if (input.getOrNull(cursor) != char) return false
        cursor++
        return true
    }
    private fun expect(char: Char) { if (!take(char)) fail("Expected '$char'") }
    private fun value(): JsonValue {
        whitespace()
        return when (input.getOrNull(cursor)) {
            '{' -> {
                cursor++
                val members = mutableListOf<Pair<String, JsonValue>>()
                if (!take('}')) {
                    do {
                        whitespace()
                        if (input.getOrNull(cursor) != '"') fail("Expected object key")
                        val name = string()
                        expect(':')
                        members.add(name to value())
                        if (take('}')) break
                        expect(',')
                    } while (!take('}'))
                }
                JsonValue.Object(members)
            }
            '[' -> {
                cursor++
                val values = mutableListOf<JsonValue>()
                if (!take(']')) {
                    do {
                        values.add(value())
                        if (take(']')) break
                        expect(',')
                    } while (!take(']'))
                }
                JsonValue.Array(values)
            }
            '"' -> JsonValue.StringValue(string())
            't' -> { literal("true"); JsonValue.BooleanValue(true) }
            'f' -> { literal("false"); JsonValue.BooleanValue(false) }
            'n' -> { literal("null"); JsonValue.Null }
            '-', in '0'..'9' -> number()
            else -> fail("Expected value")
        }
    }
    private fun literal(text: String) {
        if (!input.startsWith(text, cursor)) fail("Invalid literal")
        cursor += text.length
    }
    private fun number(): JsonValue.Number {
        val start = cursor
        if (input.getOrNull(cursor) == '-') cursor++
        if (input.getOrNull(cursor) == '0') cursor++
        else {
            if (input.getOrNull(cursor) !in '1'..'9') fail("Invalid number")
            while (input.getOrNull(cursor) in '0'..'9') cursor++
        }
        if (input.getOrNull(cursor) == '.') {
            cursor++
            if (input.getOrNull(cursor) !in '0'..'9') fail("Invalid fraction")
            while (input.getOrNull(cursor) in '0'..'9') cursor++
        }
        if (input.getOrNull(cursor) == 'e' || input.getOrNull(cursor) == 'E') {
            cursor++
            if (input.getOrNull(cursor) == '+' || input.getOrNull(cursor) == '-') cursor++
            if (input.getOrNull(cursor) !in '0'..'9') fail("Invalid exponent")
            while (input.getOrNull(cursor) in '0'..'9') cursor++
        }
        val text = input.substring(start, cursor)
        if (!JsonNumberRange.accepts(text)) fail("Number exceeds JSON double range")
        return JsonValue.Number(text)
    }
    private fun string(): String {
        expect('"')
        return buildString {
            while (cursor < input.length) {
                val char = input[cursor++]
                when {
                    char == '"' -> return@buildString
                    char == '\\' -> {
                        when (val escape = input.getOrNull(cursor++)) {
                            '"', '\\', '/' -> append(escape)
                            'b' -> append('\b')
                            'f' -> append('\u000C')
                            'n' -> append('\n')
                            'r' -> append('\r')
                            't' -> append('\t')
                            'u' -> {
                                val code = hexEscape()
                                if (code in 0xD800..0xDBFF) {
                                    if (input.getOrNull(cursor) != '\\' || input.getOrNull(cursor + 1) != 'u') fail("Invalid surrogate escape")
                                    cursor += 2
                                    val low = hexEscape()
                                    if (low !in 0xDC00..0xDFFF) fail("Invalid surrogate escape")
                                    append(code.toChar()); append(low.toChar())
                                    continue
                                }
                                if (code in 0xDC00..0xDFFF) fail("Invalid surrogate escape")
                                append(code.toChar())
                            }
                            else -> fail("Invalid escape")
                        }
                    }
                    char.code < 32 -> fail("Control character in string")
                    else -> append(char)
                }
            }
            fail("Unterminated string")
        }
    }
    private fun hexEscape(): Int {
        if (cursor + 4 > input.length) fail("Incomplete Unicode escape")
        var code = 0
        repeat(4) {
            val char = input[cursor++]
            val digit = when (char) { in '0'..'9' -> char.code - '0'.code; in 'a'..'f' -> char.code - 'a'.code + 10; in 'A'..'F' -> char.code - 'A'.code + 10; else -> fail("Invalid Unicode escape") }
            code = (code shl 4) or digit
        }
        return code
    }
}

