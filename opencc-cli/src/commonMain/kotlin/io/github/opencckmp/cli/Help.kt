/* OpenCC (Apache-2.0) and TCLAP (MIT) output adaptation; see licenses/tclap.txt. */
package io.github.opencckmp.cli

/** Native TCLAP wrapping, including comma/pipe split points and continuation indentation. */
internal fun spacePrint(text: String, width: Int = 75, indent: Int = 3, continuation: Int = 0): String = buildString {
    if (text.length + indent <= width || width <= 0) { append(" ".repeat(indent)); append(text); append('\n'); return@buildString }
    var offset = 0
    var margin = indent
    var allowed = width - indent
    while (offset < text.length) {
        var count = minOf(text.length - offset, allowed)
        if (count == allowed) while (count >= 0 && text.getOrNull(offset + count) !in listOf(' ', ',', '|')) count--
        if (count <= 0) count = allowed
        for (index in 0 until count) if (text[offset + index] == '\n') { count = index + 1; break }
        append(" ".repeat(margin)); append(text.substring(offset, offset + count)); append('\n')
        if (offset == 0) { margin += continuation; allowed -= continuation }
        while (text.getOrNull(offset + count) == ' ' && offset < text.length) offset++
        offset += count
    }
}
internal fun brief(tool: CliTool, program: String): String {
    val args = options(tool).joinToString(" ") { it.short() }
    val tail = if (tool == CliTool.Phrase) " <files> ..." else ""
    return spacePrint("$program  $args$tail", continuation = minOf(program.length + 2, 75 / 2))
}
internal fun parseFailure(tool: CliTool, program: String, error: ParseError): String =
    "PARSE ERROR: ${error.identity}\n             ${error.message}\n\nBrief USAGE: \n" + brief(tool, program) +
        "\nFor complete USAGE and HELP type: \n   $program --help\n\n"
internal fun display(tool: CliTool, program: String, version: Boolean): String {
    val title = "Open Chinese Convert (OpenCC) ${tool.title}"
    if (version) return "\n$title\nVersion: 1.4.2\n\n"
    return buildString {
        append("\n$title\nAuthor: Carbo Kuo <byvoid@byvoid.com>\nBug Report: https://github.com/BYVoid/OpenCC/issues\n\nUsage: \n\n")
        append(brief(tool, program)); append("\nOptions: \n\n")
        for (spec in options(tool)) {
            append(spacePrint(spec.long(), continuation = 3)); append(spacePrint((if (spec.required) "(required)  " else "") + spec.description, indent = 5)); append('\n')
        }
        if (tool == CliTool.Phrase) { append(spacePrint("<files>  (accepted multiple times)")); append(spacePrint("(required)  Input files", indent = 5)); append('\n') }
        append('\n'); append(spacePrint(title)); append('\n')
        if (tool == CliTool.Convert) append(builtinConfigurations)
    }
}
private val builtinConfigurations = """
Built-in Configurations:

   s2t.json    Simplified Chinese to Traditional Chinese (OpenCC Standard)
   t2s.json    Traditional Chinese (OpenCC Standard) to Simplified Chinese
   s2tw.json   Simplified Chinese to Traditional Chinese (Taiwan Standard)
   tw2s.json   Traditional Chinese (Taiwan Standard) to Simplified Chinese
   s2hk.json   Simplified Chinese to Traditional Chinese (Hong Kong variant)
   hk2s.json   Traditional Chinese (Hong Kong variant) to Simplified Chinese
   s2hkp.json  Simplified Chinese to Traditional Chinese (Hong Kong variant, with Hong Kong Phrases)
   hk2sp.json  Traditional Chinese (Hong Kong variant) to Simplified Chinese (Mainland China Phrases)
   s2twp.json  Simplified Chinese to Traditional Chinese (Taiwan Standard, with Taiwan Phrases)
   tw2sp.json  Traditional Chinese (Taiwan Standard) to Simplified Chinese (Mainland China Phrases)
   tw2t.json   Traditional Chinese (Taiwan Standard) to Traditional Chinese (OpenCC Standard)
   t2tw.json   Traditional Chinese (OpenCC Standard) to Traditional Chinese (Taiwan Standard)
   hk2t.json   Traditional Chinese (Hong Kong variant) to Traditional Chinese (OpenCC Standard)
   t2hk.json   Traditional Chinese (OpenCC Standard) to Traditional Chinese (Hong Kong variant)
   t2jp.json   Old Japanese Kanji (Kyūjitai) to New Japanese Kanji (Shinjitai)
   jp2t.json   New Japanese Kanji (Shinjitai) to Old Japanese Kanji (Kyūjitai)

""".trimStart('\n')
