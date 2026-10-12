/* Ported assertions from OpenCC ver.1.4.2 MaxMatchSegmentationTest and ConversionInspectionTest. Apache-2.0. */
package io.github.opencckmp

import kotlin.test.*

class NativeInspectionSegmentationTest {
    private val phraseDict = TextDict.fromText("太后\t太后\n头发\t頭髮\n干燥\t乾燥\n鼠标\t鼠標\n")
    private val characters = TextDict.fromText("后\t后 後\n发\t發 髮\n干\t幹 乾 干\n里\t裏 里\n")
    private fun segmenter() = MaxMatchSegmentation(DictGroup(listOf(phraseDict, characters)))
    @Test fun segment() { assertEquals(listOf("太后", "的", "头发", "干燥"), segmenter().segment("太后的头发干燥").values) }
    @Test fun mixedAsciiAndChinese() { assertEquals(listOf("Hello, world! ", "太后", "。", "头发", " done"), segmenter().segment("Hello, world! 太后。头发 done").values) }
    @Test fun emptyString() { assertEquals(0, segmenter().segment("").size) }
    @Test fun stringViewSlice() { assertEquals(listOf("太后", "的", "头发", "干燥"), segmenter().segment("x太后的头发干燥y".drop(1).dropLast(1)).values) }
    @Test fun singleCharacter() { assertEquals(listOf("一"), segmenter().segment("一").values) }
    @Test fun preserveIdeographicDescriptionSequence() { assertEquals(listOf("⿰钅干", "干"), segmenter().segment("⿰钅干干").values) }
    @Test fun truncatedUtf8Sequence() {
        val malformed = UTF8Util.encode("一") + byteArrayOf(0xE4.toByte(), 0xB8.toByte())
        val segments = segmenter().segmentUtf8(malformed)
        assertEquals(1, segments.size); assertContentEquals(malformed, segments[0])
    }

    @Test fun managedSegments() {
        val source = mutableListOf("hello", "world")
        val segments = Segments(source); source.clear()
        assertEquals(listOf("hello", "world"), segments.values)
    }
    @Test fun unmanagedSegments() { assertEquals(listOf("foo", "bar"), Segments(listOf("foo", "bar")).values) }
    @Test fun mixedSegments() { assertEquals(listOf("unmanaged", "managed", "unmanaged"), Segments(listOf("unmanaged", "managed", "unmanaged")).values) }
    @Test fun emptySegments() { assertTrue(Segments(emptyList()).values.isEmpty()) }

    private val text = "燕燕于飞差池其羽之子于归远送于野"
    private fun converter(): SimpleConverter {
        val json = """{"name":"inspection-test","segmentation":{"type":"mmseg","dict":{"type":"inline","entries":{"飞":"飛","归":"歸","远":"遠"}}},"conversion_chain":[{"dict":{"type":"inline","entries":{"飞":"飛","归":"歸","远":"遠"}}}]}"""
        return SimpleConverter.fromConfig(Config(MemoryResourceProvider(emptyMap())).newFromString(json))
    }
    @Test fun outputMatchesConvert() { val converter = converter(); assertEquals(converter.convert(text), converter.inspect(text).output) }
    @Test fun inputPreserved() { assertEquals(text, converter().inspect(text).input) }
    @Test fun segmentsNonEmpty() {
        val result = converter().inspect(text); assertTrue(result.segments.isNotEmpty()); assertEquals(text, result.segments.joinToString(""))
    }
    @Test fun stagesCountMatchesChain() { assertEquals(1, converter().inspect(text).stages.size) }
    @Test fun stageIndexStartsAtOne() { assertEquals(1, converter().inspect(text).stages.first().index) }
    @Test fun emptyInput() {
        val result = converter().inspect(""); assertEquals("", result.input); assertEquals("", result.output); assertEquals("", result.segments.joinToString(""))
    }
}
