/* Pinned OpenCC tool arguments and TCLAP output conventions. Apache-2.0. */
package io.github.opencckmp.cli

enum class CliTool(val program: String, val title: String) {
    Convert("opencc", "Command Line Tool"), Dictionary("opencc_dict", "Dictionary Tool"), Phrase("opencc_phrase_extract", "Phrase Extractor")
}
internal class Option(val name: String, val flag: String = "", val description: String,
                      val type: String? = null, val multiple: Boolean = false, val required: Boolean = false,
                      val default: String? = null, val allowed: List<String>? = null) {
    val identity: String get() = (if (flag.isEmpty()) "" else "-$flag ") + "(--$name)"
    fun short(): String = (if (required) "" else "[") + (if (flag.isEmpty()) "--$name" else "-$flag") +
        (type?.let { " <$it>" } ?: "") + (if (required) "" else "]") + (if (multiple) " ..." else "")
    fun long(): String = (if (flag.isEmpty()) "" else "-$flag${type?.let { " <$it>" } ?: ""},  ") +
        "--$name${type?.let { " <$it>" } ?: ""}" + (if (multiple) "  (accepted multiple times)" else "")
}
internal fun options(tool: CliTool): List<Option> {
    val standard = listOf(Option("ignore_rest", "-", "Ignores the rest of the labeled arguments following this flag."),
        Option("version", description = "Displays version information and exits."), Option("help", "h", "Displays usage information and exits."))
    return when (tool) {
        CliTool.Convert -> listOf(
            Option("include-tofu-risk-dictionaries", description = "Include dictionaries marked as possibly outputting tofu, i.e. Chinese characters that may render as missing-glyph boxes. By default, the command line tool skips these dictionaries."),
            Option("ambiguities", description = "Convert while streaming JSONL records that mark one-to-many (ambiguous) conversion spans."),
            Option("inspect", description = "Output full inspection result (segmentation + per-stage conversion + final output) as JSON."),
            Option("segmentation", description = "Output segmentation result as JSON instead of converted text."),
            Option("measured_result", description = "Write measured timing results as JSON to <file>.", type = "file"),
            Option("path", description = "Additional paths to locate config and dictionary files.", type = "file", multiple = true),
            Option("in-place", description = "Allow overwriting the input file when input and output refer to the same file."),
            Option("noflush", description = "Disable flush for every line", type = "bool", default = "0"),
            Option("input", "i", "Read original text from <file>.", "file"),
            Option("output", "o", "Write converted text to <file>.", "file"),
            Option("config", "c", "Configuration file", "file", default = "s2t.json")) + standard
        CliTool.Dictionary -> listOf(
            Option("input", "i", "Path to input dictionary", "file", multiple = true, required = true),
            Option("output", "o", "Path to output dictionary", "file", required = true),
            Option("from", "f", "Input format", "text|text_space|ocd2|ocd|cppjieba_utf8", required = true, allowed = listOf("text", "text_space", "ocd2", "ocd", "cppjieba_utf8")),
            Option("to", "t", "Output format", "text|ocd2|ocd", required = true, allowed = listOf("text", "ocd2", "ocd"))) + standard
        CliTool.Phrase -> listOf(Option("output", "o", "Output file", "file", required = true)) + standard
    }
}
internal class ParsedArguments(val values: Map<String, List<String>>, val files: List<String>, val zip: String?) {
    operator fun get(name: String): String? = values[name]?.lastOrNull()
    fun all(name: String): List<String> = values[name].orEmpty()
    fun flag(name: String): Boolean = values[name]?.lastOrNull() == "1"
}
internal class ParseError(val identity: String, message: String) : Exception(message)
internal class DisplayRequested(val version: Boolean) : Exception()

internal fun parse(tool: CliTool, original: List<String>): ParsedArguments {
    val specs = options(tool)
    val arguments = mutableListOf<String>()
    var zip: String? = null
    var position = 0
    while (position < original.size) {
        val arg = original[position++]
        if (tool == CliTool.Convert && (arg == "--resource-zip" || arg.startsWith("--resource-zip="))) {
            val value = if (arg == "--resource-zip") original.getOrNull(position++)?.takeUnless { it.startsWith('-') } else arg.substringAfter('=')
            if (value.isNullOrEmpty()) throw CliFailure("error: Missing value for $arg")
            if (zip != null) throw CliFailure("error: resource zip specified more than once.")
            zip = value
        } else arguments.add(arg)
    }
    val values = mutableMapOf<String, MutableList<String>>()
    val files = mutableListOf<String>()
    position = 0
    var ignore = false
    while (position < arguments.size) {
        val raw = arguments[position++]
        if (ignore) { if (tool == CliTool.Phrase) files.add(raw); continue }
        if (raw == "--" || raw == "--ignore_rest") { ignore = true; continue }
        val delimiter = raw.indexOf(' ')
        val label = if (delimiter > 1) raw.substring(0, delimiter) else raw
        var inline = if (delimiter > 1) raw.substring(delimiter + 1) else ""
        val spec = specs.firstOrNull { label == "--${it.name}" || it.flag.isNotEmpty() && label == "-${it.flag}" }
        if (spec == null || spec.type == null && raw != label) {
            // Value arguments get the first opportunity to consume embedded values. Only then
            // can TCLAP's short help switch match a combined/unrecognized short-flag argument.
            if (raw.startsWith('-') && !raw.startsWith("--") && 'h' in raw.substring(1)) throw DisplayRequested(false)
            if (tool == CliTool.Phrase) { files.add(raw); continue }
            throw ParseError("Argument: $raw", "Couldn't find match for argument")
        }
        if (!spec.multiple && values.containsKey(spec.name)) throw ParseError("Argument: ${spec.identity}", "Argument already set!")
        if (spec.name == "help" || spec.name == "version") throw DisplayRequested(spec.name == "version")
        if (spec.type == null) inline = "1" else if (inline.isEmpty()) {
            inline = arguments.getOrNull(position++) ?: throw ParseError("Argument: ${spec.identity}", "Missing a value for this argument!")
        }
        if (spec.allowed != null && inline !in spec.allowed) throw ParseError("Argument: ${spec.identity}", "Value '$inline' does not meet constraint: ${spec.type}")
        if (spec.type == "bool") inline = boolean(inline, spec.identity)
        values.getOrPut(spec.name) { mutableListOf() }.add(inline)
    }
    val missing = specs.filter { it.required && !values.containsKey(it.name) }.map { it.name }.toMutableList()
    if (tool == CliTool.Phrase && files.isEmpty()) missing.add("fileName")
    if (missing.isNotEmpty()) throw ParseError(" ", "Required argument${if (missing.size == 1) "" else "s"} missing: ${missing.joinToString(", ")}")
    for (spec in specs) if (!values.containsKey(spec.name) && spec.default != null) values[spec.name] = mutableListOf(spec.default)
    return ParsedArguments(values, files, zip)
}
private fun boolean(text: String, identity: String): String {
    fun failed(): Nothing = throw ParseError("Argument: $identity", "Couldn't read argument value from string '$text'")
    if (text.isEmpty()) return "0"
    var index = 0
    var count = 0
    var last = 0
    while (index < text.length) {
        while (index < text.length && text[index] in " \t\n\r\u000b\u000c") index++
        if (index == text.length) failed()
        val start = index
        if (text[index] == '+' || text[index] == '-') index++
        val digits = index
        while (index < text.length && text[index] in '0'..'9') index++
        if (digits == index) failed()
        last = text.substring(start, index).toIntOrNull()?.takeIf { it in 0..1 } ?: failed()
        count++
    }
    if (count > 1) throw ParseError("Argument: $identity", "More than one valid value parsed from string '$text'")
    return last.toString()
}
internal class CliFailure(message: String) : Exception(message)
