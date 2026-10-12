/* OpenCC dictionary conversion adapted from Copyright 2010-2026 Carbo Kuo and contributors. Apache-2.0. */
package io.github.opencckmp.tools

import io.github.opencckmp.*

enum class DictionaryFormat(val formatName: String) {
    Text("text"), TextSpace("text_space"), Ocd("ocd"), Ocd2("ocd2"), CppJiebaUtf8("cppjieba_utf8");
    companion object {
        fun fromName(name: String): DictionaryFormat = entries.firstOrNull { it.formatName == name }
            ?: throw InvalidFormat("Unknown dictionary format: $name")
    }
}

/** All upstream input/output formats, with host I/O separated from dictionary conversion. */
object DictionaryConverter {
    fun load(inputs: List<ByteArray>, format: DictionaryFormat): Dict {
        if (format == DictionaryFormat.CppJiebaUtf8) return cppJieba(inputs)
        if (inputs.size != 1) throw InvalidFormat("Dictionary format '${format.formatName}' requires exactly one input dictionary.")
        val bytes = inputs.single()
        return when (format) {
            DictionaryFormat.Text -> TextDict.fromBytes(bytes)
            DictionaryFormat.TextSpace -> TextDict.fromText(UTF8Util.decode(bytes), ' ')
            DictionaryFormat.Ocd -> DartsDict.fromBytes(bytes)
            DictionaryFormat.Ocd2 -> MarisaDict.fromBytes(bytes)
            DictionaryFormat.CppJiebaUtf8 -> error("Handled above")
        }
    }

    fun write(dict: Dict, format: DictionaryFormat, byteOrder: DictionaryByteOrder = DictionaryByteOrder.LittleEndian): ByteArray = when (format) {
        DictionaryFormat.Text -> DictionaryWriters.text(dict)
        DictionaryFormat.Ocd -> DictionaryWriters.ocd(dict, byteOrder)
        DictionaryFormat.Ocd2 -> DictionaryWriters.ocd2(dict, byteOrder)
        else -> throw InvalidFormat("Dictionary format '${format.formatName}' is input-only")
    }

    fun convert(input: ByteArray, from: DictionaryFormat, to: DictionaryFormat): ByteArray = convert(listOf(input), from, to)
    fun convert(inputs: List<ByteArray>, from: DictionaryFormat, to: DictionaryFormat): ByteArray = write(load(inputs, from), to)

    private fun cppJieba(inputs: List<ByteArray>): Dict {
        if (inputs.isEmpty()) throw InvalidFormat("cppjieba_utf8 requires at least one input dictionary.")
        val entries = mutableMapOf<String, List<String>>()
        fun asciiSpace(char: Char): Boolean = char == ' ' || char == '\t' || char == '\r' || char == '\n' || char == '\u000B' || char == '\u000C'
        for ((file, bytes) in inputs.withIndex()) {
            UTF8Util.decode(bytes).lineSequence().forEachIndexed { lineNumber, line ->
                if (line.isEmpty() || line.all { asciiSpace(it) }) return@forEachIndexed
                val tokens = mutableListOf<String>()
                var cursor = 0
                while (cursor < line.length) {
                    while (cursor < line.length && asciiSpace(line[cursor])) cursor++
                    if (cursor == line.length) break
                    val start = cursor
                    while (cursor < line.length && !asciiSpace(line[cursor])) cursor++
                    tokens.add(line.substring(start, cursor))
                }
                val values = if (file == 0) {
                    if (tokens.size != 3) throw InvalidFormat("Invalid cppjieba_utf8 base dict line: ${lineNumber + 1}")
                    listOf(tokens[1], tokens[2], "base")
                } else when (tokens.size) {
                    1 -> listOf("", "", "user_default")
                    2 -> listOf("", tokens[1], "user_default")
                    3 -> listOf(tokens[1], tokens[2], "user_freq")
                    else -> throw InvalidFormat("Invalid cppjieba_utf8 user dict line: ${lineNumber + 1}")
                }
                entries[tokens.first()] = values
            }
        }
        return TextDict(Lexicon(entries.map { DictEntry(it.key, it.value) }).sorted())
    }
}
