/* Pinned PrefixMatchTest.cpp observable assertion ports; C++ aliasing/state maps to Kotlin ownership. Apache-2.0. */
package io.github.opencckmp

import kotlin.test.*

class NativePrefixScanTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun dict(vararg entries: Pair<String, String>) = TextDict(Lexicon(entries.map { DictEntry(it.first, listOf(it.second)) }).sorted())
    private fun chinese() = dict("太后" to "太后", "里" to "裏")
    private fun base() = MarisaDict.fromBytes(nativePrefixScanResources.getValue("base.ocd2"))
    @Test fun nativeSkipDistances() {
        val provider = MemoryResourceProvider(nativePrefixScanResources)
        val scans = nativePrefixScanConfigs.mapValues { PrefixScan(Config(provider).newFromString(it.value).conversionChain!!.conversions.first().dict) }
        for (row in nativePrefixScanCases) {
            val fields = row.split('|'); val input = bytes(fields[1]); val expected = fields[2].toInt(); val scan = scans.getValue(fields[0])
            assertEquals(expected, scan.skipUnmatchableUtf8(input), row)
            val text = try { UTF8Util.decode(input) } catch (_: InvalidUTF8) { continue }
            assertEquals(UTF8Util.decode(input, 0, expected).length, scan.skipUnmatchable(text), row)
        }
    }
    @Test fun singleDictFastPathMatchesTablePath() {
        val loaded = base(); val fast = DictGroup(listOf(loaded)); val table = DictGroup(listOf(loaded, TextDict.fromText(UTF8Util.decode(nativePrefixScanResources.getValue("base.txt")))))
        for (query in listOf("清華", "清華大", "清華大學", "清", "積羽沉舟", "nowhere")) {
            val first = fast.matchPrefix(query); val second = table.matchPrefix(query)
            assertEquals(first?.key, second?.key); assertEquals(first?.defaultValue, second?.defaultValue); assertEquals(first?.keyByteLength, second?.keyByteLength)
        }
    }
    @Test fun marisaLoadedStorageIsOwned() {
        val image = nativePrefixScanResources.getValue("base.ocd2").copyOf(); val loaded = MarisaDict.fromBytes(image); image.fill(0)
        val match = assertNotNull(loaded.matchPrefix("清華")); assertEquals("清華", match.key); assertEquals("Tsinghua", match.defaultValue); assertEquals(6, match.keyByteLength)
        assertSame(match, loaded.matchPrefix("清華")) // Stable Kotlin values replace native reconstruction-state/borrowed pointers.
    }
    @Test fun fastPathSelectsLongestMatch() {
        val match = assertNotNull(base().matchPrefix("清華大學校園"))
        assertEquals("清華大學", match.key); assertEquals("TsinghuaUniversity", match.defaultValue); assertEquals(UTF8Util.byteLength(match.key), match.keyByteLength)
    }
    @Test fun matchPrefixViewFastPathReturnsOwnedValues() {
        val input = UTF8Util.encode("清華大學校園"); val match = assertNotNull(base().matchPrefixUtf8(input)); input.fill(0)
        assertEquals("清華大學", match.key); assertEquals("TsinghuaUniversity", match.defaultValue); assertEquals(12, match.keyByteLength)
    }
    @Test fun matchPrefixViewTablePathReturnsOwnedValues() {
        val loaded = base(); val match = assertNotNull(DictGroup(listOf(loaded, loaded)).matchPrefix("清華大學校園"))
        assertEquals("清華大學", match.key); assertEquals("TsinghuaUniversity", match.defaultValue); assertEquals(12, match.keyByteLength)
    }
    @Test fun shortCircuitGroupPrefersEarlierShorterMatch() {
        val match = assertNotNull(DictGroup(listOf(dict("意大利" to "義大利"), dict("意大利面" to "義大利麵"))).matchPrefix("意大利面"))
        assertEquals("意大利", match.key); assertEquals("義大利", match.defaultValue)
    }
    @Test fun unionGroupPrefersLaterLongerMatch() {
        val match = assertNotNull(UnionDictGroup(listOf(dict("意大利" to "義大利"), dict("意大利面" to "義大利麵"))).matchPrefix("意大利面"))
        assertEquals("意大利面", match.key); assertEquals("義大利麵", match.defaultValue)
    }
    @Test fun unionGroupPreservesNestedShortCircuitBoundary() {
        val nested = DictGroup(listOf(dict("意" to "nested-first"), dict("意大利面" to "hidden")))
        val match = assertNotNull(UnionDictGroup(listOf(nested, dict("意大利" to "root-second"))).matchPrefix("意大利面"))
        assertEquals("意大利", match.key); assertEquals("root-second", match.defaultValue)
    }
    @Test fun asciiRunLength() {
        val scan = PrefixScan(dict("一" to "一"))
        for (length in 0 until 70) { val input = UTF8Util.encode("a".repeat(length) + "一")
            for (start in 0..length) assertEquals(length - start, scan.skipUnmatchableUtf8(input, start)) }
        assertEquals(100, scan.skipUnmatchableUtf8(UTF8Util.encode("x".repeat(100))))
        assertEquals(0, scan.skipUnmatchableUtf8(UTF8Util.encode("一"))); assertEquals(0, scan.skipUnmatchableUtf8(byteArrayOf()))
    }
    @Test fun skipUnmatchableStopsAtAsciiCandidates() {
        val scan = PrefixScan(base()); val input = UTF8Util.encode("abc BYVoid")
        assertEquals(4, scan.skipUnmatchableUtf8(input)); assertEquals(0, scan.skipUnmatchableUtf8(input, 4)); assertEquals(0, scan.skipUnmatchable("zoo"))
    }
    @Test fun skipNeverCrossesAnyKeyFirstChar() {
        val base = base(); for (scan in listOf(PrefixScan(base), PrefixScan(DictGroup(listOf(base, base)))))
            for (entry in base.lexicon) assertEquals(0, scan.skipUnmatchableUtf8(UTF8Util.encode(entry.key)))
    }
    @Test fun skipsLongAsciiRuns() { for (text in listOf("x".repeat(100), "x".repeat(100) + "太后")) assertEquals(100, chinese().skipUnmatchableUtf8(UTF8Util.encode(text))) }
    @Test fun skipsNonCandidateCjkCharacters() {
        val prefix = "、。「」あい"; val scan = PrefixScan(chinese())
        assertEquals(UTF8Util.byteLength(prefix), scan.skipUnmatchableUtf8(UTF8Util.encode(prefix)))
        assertEquals(UTF8Util.byteLength(prefix), scan.skipUnmatchableUtf8(UTF8Util.encode(prefix + "太后"))); assertEquals(0, scan.skipUnmatchable("太x"))
    }
    @Test fun fourByteCharactersUseLeadByteFiltering() {
        assertEquals(9, chinese().skipUnmatchableUtf8(UTF8Util.encode("🎉🚀x太后")))
        val scan = PrefixScan(dict("𠀀" to "X")); for (text in listOf("𠀀", "🎉")) assertEquals(0, scan.skipUnmatchableUtf8(UTF8Util.encode(text)))
    }
    @Test fun charLevelSkipsSameLeadByteNonCandidates() { val scan = PrefixScan(chinese()); assertEquals(6, scan.skipUnmatchableUtf8(UTF8Util.encode("天地太后"))); assertEquals(3, scan.skipUnmatchableUtf8(UTF8Util.encode("黑里"))) }
    @Test fun charLevelSkipsNonIdsPunctuationWithLead0xE2() {
        val scan = PrefixScan(chinese()); val text = "——“”…"; assertEquals(UTF8Util.byteLength(text), scan.skipUnmatchableUtf8(UTF8Util.encode(text))); assertEquals(3, scan.skipUnmatchableUtf8(UTF8Util.encode("—⿰氵青")))
    }
    @Test fun charLevelSkipsTwoByteCharacters() { val input = UTF8Util.encode("Ünïcodé tail 太"); assertEquals(input.size - 3, chinese().skipUnmatchableUtf8(input)) }
    @Test fun allIdsOperatorsStopTheScan() { val scan = PrefixScan(chinese()); for (point in 0x2FF0..0x2FFF) assertEquals(0, scan.skipUnmatchableUtf8(UTF8Util.encode(point.toChar().toString()))) }
    @Test fun stopsAtIdeographicDescriptionOperator() {
        val input = UTF8Util.encode("abc⿰氵青"); val scan = PrefixScan(chinese()); assertEquals(3, scan.skipUnmatchableUtf8(input)); assertEquals(0, scan.skipUnmatchableUtf8(input, 3))
    }
    @Test fun stopsAtInvalidOrTruncatedSequences() {
        val scan = PrefixScan(chinese()); val invalid = byteArrayOf(97, 98, 0xFF.toByte()); assertEquals(2, scan.skipUnmatchableUtf8(invalid))
        assertEquals(2, scan.skipUnmatchableUtf8(byteArrayOf(97, 98, 0xE3.toByte()))); assertEquals(0, scan.skipUnmatchableUtf8(invalid, 2))
    }
    @Test fun skipUnmatchableTablePath() {
        val loaded = base(); val scan = PrefixScan(DictGroup(listOf(loaded, loaded))); val input = UTF8Util.encode("abc 清華")
        assertEquals(4, scan.skipUnmatchableUtf8(input)); assertEquals(0, scan.skipUnmatchableUtf8(input, 4))
        assertFailsWith<IllegalArgumentException> { scan.skipUnmatchable("😀", 0, 1) }
    }
}
