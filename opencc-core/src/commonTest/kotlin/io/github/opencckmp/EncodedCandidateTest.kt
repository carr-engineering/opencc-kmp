@file:OptIn(ExperimentalOpenCCApi::class)
package io.github.opencckmp

import kotlin.test.*

class EncodedCandidateTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun dict(vararg entries: Pair<String, List<String>>) = TextDict(Lexicon(entries.map { DictEntry(it.first, it.second) }).sorted())
    private fun converter(vararg dictionaries: Dict): Converter = SingleStageConverter(
        MaxMatchSegmentation(dictionaries.firstOrNull() ?: dict()), ConversionChain(dictionaries.map { Conversion(it) }))
    private fun candidates(converter: Converter, word: String) = converter.getAllConversionsUtf8(UTF8Util.encode(word)).map { UTF8Util.decode(it) }

    @Test fun nativeCandidateGoldenCases() {
        val provider = MemoryResourceProvider(nativeCandidateResources.mapValues { UTF8Util.encode(it.value) })
        val converters = nativeCandidateConfigs.mapValues { Config(provider).newFromString(it.value) }
        for (row in nativeCandidateCases) {
            val fields = row.split('|'); val input = bytes(fields[1]); val converter = converters.getValue(fields[0])
            if (fields[2] == "ERROR") assertFailsWith<InvalidUTF8>(row) { converter.getAllConversionsUtf8(input) }
            else assertEquals(fields.drop(3), converter.getAllConversionsUtf8(input).map { value ->
                value.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            }, row)
        }
    }
    @Test fun singleDictMultipleValues() {
        assertEquals(listOf("里", "裏"), candidates(converter(dict("里" to listOf("里", "裏"))), "里"))
    }
    @Test fun chainedExpansionCarriesBranches() {
        assertEquals(listOf("里", "裡"), candidates(converter(dict("里" to listOf("里", "裏")), dict("裏" to listOf("裡"))), "里"))
    }
    @Test fun duplicateCandidatesAreDeduped() {
        assertEquals(listOf("面"), candidates(converter(dict("面" to listOf("麵", "面")), dict("麵" to listOf("面"))), "面"))
    }
    @Test fun wordNotInAnyDictReturnsEmpty() { assertTrue(candidates(converter(dict("里" to listOf("裏"))), "国").isEmpty()) }
    @Test fun prefixOnlyMatchReturnsEmpty() { assertTrue(candidates(converter(dict("里" to listOf("裏"))), "里面").isEmpty()) }
    @Test fun partialConversionInLaterDict() {
        assertEquals(listOf("裡面"), candidates(converter(dict("里面" to listOf("裏面")), dict("裏" to listOf("裡"))), "里面"))
    }
    @Test fun pipelineConverterYieldsEmpty() {
        val pipeline = PipelineConverter(listOf(converter(dict("里" to listOf("裏")))))
        assertNull(pipeline.conversionChain); assertTrue(candidates(pipeline, "里").isEmpty())
    }
    @Test fun keyOnlyEntryPassesWordThrough() { assertEquals(listOf("通道"), candidates(converter(dict("通道" to emptyList())), "通道")) }
    @Test fun truncatedUtf8InputIsSafe() {
        val converter = converter(dict("里" to listOf("裏"))); val tail = bytes("e987")
        assertTrue(converter.getAllConversionsUtf8(tail).isEmpty())
        assertTrue(converter.getAllConversionsUtf8(UTF8Util.encode("里") + tail).isEmpty())
    }
    @Test fun idsKeptAtomicInLaterDict() {
        assertEquals(listOf("⿱艹里"), candidates(converter(dict("里字" to listOf("⿱艹里")), dict("里" to listOf("裏"))), "里字"))
    }
    @Test fun normalizationPrePassApplied() {
        val converter = ConfigBasedConverter(converter(dict("里" to listOf("里"))), converter(dict("里" to listOf("里", "裏"))))
        assertEquals(listOf("里", "裏"), candidates(converter, "里"))
    }
    @Test fun ownedArraysByteRangesAndNulModes() {
        val converter = converter(dict("a" to listOf("x", "y")))
        val input = UTF8Util.encode("!a\u0000tail?")
        assertTrue(converter.getAllConversionsUtf8(input, 1, input.size - 1).isEmpty())
        val first = converter.getAllConversionsUtf8(input, 1, input.size - 1, EncodedInputMode.NullTerminated)
        assertEquals(listOf("x", "y"), first.map { UTF8Util.decode(it) })
        first[0][0] = 0; input.fill(0)
        assertEquals(listOf("x", "y"), candidates(converter, "a"))
        assertFailsWith<IllegalArgumentException> { converter.getAllConversionsUtf8(byteArrayOf(), endIndex = 1) }
    }
    @Test fun nativeZeroValueReaderCases() {
        for (row in nativeZeroValueCases) {
            val fields = row.split('|'); val image = bytes(fields[1]); val entries = fields.drop(3)
            if (fields[0] == "binary") {
                val dictionary = BinaryDict.fromBytes(image)
                assertEquals(entries, dictionary.lexicon.entries.map { entry ->
                    UTF8Util.encode(entry.key).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') } + ":" + entry.numValues
                }, row)
                for (entry in dictionary.lexicon) if (entry.numValues == 0) assertEquals(entry.key, entry.defaultValue)
            } else {
                val values = SerializedValues.fromBytes(image)
                assertEquals(entries, values.values.map { ":" + it.size }, row)
            }
        }
    }
}
