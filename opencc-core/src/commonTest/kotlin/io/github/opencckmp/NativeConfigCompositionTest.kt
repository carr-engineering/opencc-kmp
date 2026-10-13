@file:OptIn(ExperimentalOpenCCApi::class)
package io.github.opencckmp

import kotlin.test.*

class NativeConfigCompositionTest {
    private fun chain(vararg dictionaries: String) = """{"name":"native fixture","conversion_chain":[${dictionaries.joinToString(",") { "{\"dict\":$it}" }}]}"""
    @Test fun groupDictWithoutMatchPolicyDefaultsToShortCircuit() {
        val warnings = mutableListOf<ConfigWarning>()
        val provider = MemoryResourceProvider(mapOf("phrases.txt" to "燕燕于飞\t燕燕于飛\n".encodeToByteArray()))
        val c = Config(provider, warningSink = { warnings.add(it) }).newFromString("""{"name":"legacy","segmentation":{"type":"mmseg","dict":{"type":"text","file":"phrases.txt"}},"conversion_chain":[{"dict":{"type":"group","dicts":[{"type":"inline","entries":{"燕燕于飞":"自訂覆寫"}},{"type":"text","file":"phrases.txt"}]}}]}""")
        assertTrue(warnings.any { it.message.contains("config does not conform to schema") }); assertEquals("自訂覆寫", c.convert("燕燕于飞"))
    }
    @Test fun inlineSegmentationDictUsesLongestMatch() {
        val c = Config().newFromString("""{"name":"longest","segmentation":{"type":"mmseg","dict":{"type":"inline","entries":{"ABC":"ABC","AB":"AB","D":"D"}}},"conversion_chain":[{"dict":{"type":"inline","entries":{"ABC":"X","AB":"Y","C":"Z","D":"D"}}}]}""")
        assertEquals("XD", c.convert("ABCD"))
    }
    @Test fun inlineDictPreservesExactStringSemantics() {
        val c = Config().newFromString("""{"name":"padded","segmentation":{"type":"mmseg","dict":{"type":"inline","entries":{"A":"A"," A ":" A "}}},"conversion_chain":[{"dict":{"type":"inline","entries":{" A ":"B"}}}]}""")
        assertEquals("A", c.convert("A")); assertEquals("B", c.convert(" A "))
    }
    @Test fun normalizationMissingFileDictThrows() {
        assertFailsWith<FileNotFound> { Config().newFromString("""{"name":"missing","normalization":[{"dict":{"type":"ocd2","file":"nonexistent.ocd2"}}],"conversion_chain":[{"dict":{"type":"inline","entries":{"甲":"丙"}}}]}""") }
    }
    @Test fun normalizationGetConversionChainIsNonNull() {
        val c = Config().newFromString("""{"name":"normalization","normalization":[{"dict":{"type":"inline","entries":{"甲":"乙"}}}],"conversion_chain":[{"dict":{"type":"inline","entries":{"乙":"丙"}}}]}""")
        val main = assertNotNull(c.conversionChain); assertTrue(main.conversions.isNotEmpty())
        val d = main.conversions.first().dict; assertNotNull(d.match("乙")); assertNull(d.match("甲"))
        assertEquals("丙", main.convert("乙")); assertEquals("丙", c.convert("甲"))
    }
    // The native Rime helper is test code, not a public operation. Compose the public
    // candidate operation with main-chain fallback, preserving its boolean and vector.
    private fun rime(c: Converter, input: String): Pair<Boolean, List<String>> {
        val candidates = c.getAllConversions(input)
        return candidates.isNotEmpty() to if (candidates.isNotEmpty()) candidates else listOf(assertNotNull(c.conversionChain).convert(input))
    }
    @Test fun rimeConvertWordExactExpansionThreadsThroughChain() {
        val c = Config(MemoryResourceProvider(mapOf("first.txt" to "里\t裏 里 哩\n".encodeToByteArray()))).newFromString(chain("""{"type":"text","file":"first.txt"}""", """{"type":"inline","entries":{"裏":"裡"}}"""))
        assertEquals(true to listOf("裡", "里", "哩"), rime(c, "里"))
    }
    @Test fun rimeConvertWordPartialFallbackContinuesToNextStage() {
        val c = Config().newFromString(chain("""{"type":"inline","entries":{"内":"內"}}""", """{"type":"inline","entries":{"內存":"記憶體"}}"""))
        assertEquals(true to listOf("記憶體"), rime(c, "内存"))
    }
    @Test fun rimeConvertWordReturnsFalseWithoutAnyExactMatch() {
        val c = Config().newFromString(chain("""{"type":"inline","entries":{"a":"A"}}""", """{"type":"inline","entries":{"z":"Z"}}"""))
        assertEquals(false to listOf("Abc"), rime(c, "abc"))
    }
}
