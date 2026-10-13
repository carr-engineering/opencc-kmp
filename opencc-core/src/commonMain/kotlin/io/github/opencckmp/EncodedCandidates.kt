/* Adapted from OpenCC ConversionCandidates.cpp. Apache-2.0. */
@file:OptIn(ExperimentalOpenCCApi::class)
package io.github.opencckmp

private class CandidateBytes(bytes: ByteArray) {
    val value = bytes.copyOf()
    private val hash = value.contentHashCode()
    override fun hashCode(): Int = hash
    override fun equals(other: Any?): Boolean = other is CandidateBytes && value.contentEquals(other.value)
}

private fun Dict.exactCandidate(input: ByteArray): DictEntry? {
    // Exact group lookup tries every child in order. Prefix short-circuit policy
    // must not hide an exact match in a later child behind an earlier shorter prefix.
    val children = groupItems
    if (children != null) return children.firstNotNullOfOrNull { it.exactCandidate(input) }
    return matchPrefixUtf8(input)?.takeIf { it.keyByteLength == input.size }
}

/**
 * Discovery-ordered, deduplicated encoded candidates. An exact match in some main-chain stage
 * is required. Prefix fallback preserves native partial/non-canonical UTF8 traversal; normalization
 * runs before candidate expansion. Input/output ranges count bytes and returned arrays are owned.
 */
@ExperimentalOpenCCApi
fun Converter.getAllConversionsUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size,
    mode: EncodedInputMode = EncodedInputMode.LengthDelimited): List<ByteArray> {
    val end = EncodedUTF8.end(input, startIndex, endIndex, mode)
    val converter = unwrapped()
    val chain = converter.conversionChain ?: return emptyList()
    val word = input.copyOfRange(startIndex, end)
    var current = listOf(if (converter is ConfigBasedConverter) converter.normalization.convertUtf8(word) else word)
    var matched = false
    for (conversion in chain.conversions) {
        val next = linkedSetOf<CandidateBytes>()
        for (candidate in current) {
            val entry = conversion.dict.exactCandidate(candidate)
            if (entry == null) next.add(CandidateBytes(conversion.convertUtf8(candidate))) else {
                matched = true
                if (entry.numValues == 0) next.add(CandidateBytes(candidate))
                else for (value in entry.values) next.add(CandidateBytes(UTF8Util.encode(value)))
            }
        }
        current = next.map { it.value }
    }
    return if (matched) current.map { it.copyOf() } else emptyList()
}
