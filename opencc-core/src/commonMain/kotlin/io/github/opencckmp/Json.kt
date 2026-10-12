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
        val value = value(0)
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
    private fun value(depth: Int): JsonValue {
        if (depth > 256) fail("Nesting limit exceeded")
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
                        members.add(name to value(depth + 1))
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
                        values.add(value(depth + 1))
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
        return JsonValue.Number(input.substring(start, cursor))
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
                                if (cursor + 4 > input.length) fail("Incomplete Unicode escape")
                                val code = input.substring(cursor, cursor + 4).toIntOrNull(16) ?: fail("Invalid Unicode escape")
                                cursor += 4
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
}

