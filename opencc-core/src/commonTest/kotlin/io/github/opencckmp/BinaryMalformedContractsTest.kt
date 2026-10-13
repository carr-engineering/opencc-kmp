package io.github.opencckmp

import kotlin.test.*

/** Exact crafted fields from OpenCC ver.1.4.2, including its 32/64-bit layouts. */
class BinaryMalformedContractsTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(text: String) = UTF8Util.encode(text).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private fun verify(vararg names: String) {
        for (name in names) {
            val (operation, encoded, expected) = nativeBinaryMalformedCases.getValue(name)
            val image = bytes(encoded)
            fun read(): String = when (operation) {
                "values" -> "OK" + SerializedValues.fromBytes(image).values.joinToString("") { values ->
                    "|:" + values.joinToString(",") { hex(it) }
                }
                "prefix65" -> "OK|" + hex(DartsDict.fromBytes(image).matchPrefix("a".repeat(65))!!.defaultValue)
                else -> {
                    val dictionary = when (operation) {
                        "binary" -> BinaryDict.fromBytes(image)
                        "marisa" -> MarisaDict.fromBytes(image)
                        "darts" -> DartsDict.fromBytes(image)
                        else -> error("Unexpected runtime operation: $operation")
                    }
                    "OK" + dictionary.lexicon.entries.joinToString("") { entry ->
                        "|" + hex(entry.key) + ":" + entry.values.joinToString(",") { hex(it) }
                    }
                }
            }
            if (expected == "InvalidFormat") assertFailsWith<InvalidFormat>(name) { read() }
            else assertEquals(expected, read(), name)
        }
    }

    @Test fun binaryRejectsHugeKeyTotalLength() = verify("binary-huge-keys-4", "binary-huge-keys-8")
    @Test fun binaryRejectsHugeValueTotalLength() = verify("binary-huge-values-4", "binary-huge-values-8")
    @Test fun binaryRejectsKeyOffsetOutOfBounds() = verify("binary-key-offset")
    @Test fun binaryRejectsValueOffsetOutOfBounds() = verify("binary-value-offset")
    @Test fun binaryAcceptsWellFormedFile() = verify("binary-valid-4")
    @Test fun binaryAcceptsLegacy64BitLayout() = verify("binary-valid-8")
    @Test fun binaryEmptyFixedWidthIsNotMisdetectedAsLegacy() {
        val image = bytes(nativeBinaryMalformedCases.getValue("binary-empty").second)
        assertEquals(12, image.size)
        assertTrue(image.all { it == 0.toByte() })
        verify("binary-empty")
    }
    @Test fun serializedValuesRejectsValueOffsetOutOfBounds() = verify("values-offset")
    @Test fun serializedValuesRejectsValueNotNullTerminated() = verify("values-terminator")
    @Test fun serializedValuesRejectsZeroValueBytes() = verify("values-zero")
    @Test fun serializedValuesRejectsCumulativeOffsetOverflow() = verify("values-cumulative")
    @Test fun serializedValuesAcceptsWellFormedFile() {
        verify("values-valid")
        val image = bytes(nativeBinaryMalformedCases.getValue("values-valid").second)
        assertEquals(image.size, SerializedValues.fromBytes(image).bytesRead)
    }
    @Test fun serializedValuesRejectsHugeValueTotalLength() = verify("values-huge")
    @Test fun dartsRejectsHugeDartsSize() = verify("darts-huge")
    @Test fun dartsRejectsMisalignedDartsSize() = verify("darts-alignment")
    @Test fun dartsRejectsInvalidDartsRoot() = verify("darts-root")
    @Test fun dartsRejectsInvalidDartsValue() = verify("darts-value")
    @Test fun dartsLoadsLegacy64bitFormat() {
        verify("darts-legacy")
        val dictionary = DartsDict.fromBytes(bytes(nativeBinaryMalformedCases.getValue("darts-legacy").second))
        for (entry in dictionary.lexicon) {
            assertEquals(entry.values, dictionary.match(entry.key)?.values)
            assertEquals(entry.key, dictionary.matchPrefix(entry.key + "!")?.key)
        }
    }
    @Test fun dartsRejectsInvalidLegacy64Root() = verify("darts-legacy-root")
    @Test fun dartsLegacy64PrefixSearchBeyond64Matches() {
        verify("darts-prefix65")
        val dictionary = DartsDict.fromBytes(bytes(nativeBinaryMalformedCases.getValue("darts-prefix65").second))
        assertEquals((65 downTo 1).map { "a".repeat(it) }, dictionary.matchAllPrefixes("a".repeat(65)).map { it.key })
        assertEquals("v65", dictionary.matchPrefix("a".repeat(65))?.defaultValue)
    }
    @Test fun marisaRejectsCorruptTrieData() = verify("marisa-corrupt")
    @Test fun allNineNativeZeroValueImagesRetainOrderedCandidateData() {
        verify(*(0..8).map { "zero-$it" }.toTypedArray())
        assertEquals(9, nativeZeroValueCases.size)
        for (row in nativeZeroValueCases) {
            val fields = row.split('|')
            if (fields[0] == "binary") for (entry in BinaryDict.fromBytes(bytes(fields[1])).lexicon) {
                if (entry.numValues == 0) assertEquals(entry.key, entry.defaultValue)
            }
        }
    }
}
