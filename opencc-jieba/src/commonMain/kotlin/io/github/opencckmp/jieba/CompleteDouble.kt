package io.github.opencckmp.jieba

// Unlike model atof fields, merged frequency metadata requires strtod to consume the whole C string.
private val completeDouble = Regex(
    "^[\\t\\r\\n\\u000b\\u000c ]*[+-]?(?:" +
        "(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|" +
        "0x(?:[0-9a-f]+(?:\\.[0-9a-f]*)?|\\.[0-9a-f]+)(?:p[+-]?[0-9]+)?|" +
        "inf(?:inity)?|nan(?:\\([a-z0-9_]*\\))?)$", RegexOption.IGNORE_CASE)

internal fun cDoubleComplete(text: String): Double? {
    val token = text.substringBefore('\u0000')
    // C numeric spellings are ASCII; Unicode case folding must not accept, for example, dotless i.
    if (token.any { it.code > 127 }) return null
    return if (completeDouble.matches(token)) cDouble(token) else null
}
