@file:OptIn(ExperimentalOpenCCApi::class)
package io.github.opencckmp

import kotlin.test.*

class EncodedAmbiguitiesTest {
    private fun converter() = SingleStageConverter(null, ConversionChain(listOf(Conversion(
        TextDict.fromText("汉\t漢 寒\n里\t裡 裏\n𠀀\t字 詞\n")))))
    @Test fun byteOffsetsComposeAndMatchEquivalentStringSpans() {
        val input = "x汉里𠀀y"
        val converter = converter()
        val text = converter.convertWithAmbiguities(input)
        val encoded = converter.convertWithAmbiguitiesUtf8(input.encodeToByteArray())
        assertEquals(text.output, UTF8Util.decode(encoded.output))
        assertEquals(text.sources, encoded.sources.map { UTF8Util.decode(it) })
        assertEquals(text.ambiguities.size, encoded.ambiguities.size)
        for ((ordinary, bytes) in text.ambiguities.zip(encoded.ambiguities)) {
            assertEquals(UTF8Util.byteLength(text.output.substring(0, ordinary.outputOffset)), bytes.outputOffset)
            assertEquals(UTF8Util.byteLength(text.output.substring(ordinary.outputOffset, ordinary.outputOffset + ordinary.outputLength)), bytes.outputLength)
            assertEquals(ordinary.sourceIndex, bytes.sourceIndex)
        }
        val normalized = ConfigBasedConverter(SingleStageConverter(null, ConversionChain(listOf(Conversion(TextDict.fromText("神\t汉\n"))))), converter)
        assertEquals(listOf("汉"), normalized.convertWithAmbiguitiesUtf8("神".encodeToByteArray()).sources.map { UTF8Util.decode(it) })
    }
    @Test fun noncanonicalPartialNulAndUnavailableAnalysisRemainEncoded() {
        val bytes = byteArrayOf(0xe4.toByte(), 0x41, 0x41, 0, 0xe4.toByte(), 0xb8.toByte())
        val result = converter().convertWithAmbiguitiesUtf8(bytes)
        assertContentEquals(bytes, result.output)
        assertTrue(result.ambiguities.isEmpty())
        assertContentEquals(bytes.copyOf(3), converter().convertWithAmbiguitiesUtf8(bytes, mode = EncodedInputMode.NullTerminated).output)
        val pipeline = PipelineConverter(listOf(converter(), converter()))
        assertFalse(pipeline.convertWithAmbiguitiesUtf8("汉".encodeToByteArray()).analyzed)
        assertContentEquals(pipeline.convertUtf8("汉".encodeToByteArray()), pipeline.convertWithAmbiguitiesUtf8("汉".encodeToByteArray()).output)
    }
    @Test fun streamingSourceIdentityAndReturnedArraysAreSeparatelyOwned() {
        val stream = Utf8AmbiguityStream(converter(), 0)
        val a = stream.convertChunk("汉里".encodeToByteArray())
        assertEquals(listOf("汉", "里"), a.newSources.map { UTF8Util.decode(it) })
        a.newSources[0][0] = 0
        val b = stream.finish("汉里".encodeToByteArray())
        assertTrue(b.newSources.isEmpty())
        assertEquals(listOf(0, 1), b.ambiguities.map { it.sourceIndex })
        assertEquals(2, stream.sourceCount)
        assertContentEquals(byteArrayOf(), stream.finish().output)
    }
    @Test fun errorContextOwnsFailingSuffixAndRespectsRange() {
        val input = byteArrayOf(65, 0x80.toByte(), 66, 67)
        val error = assertFailsWith<InvalidUTF8> { converter().convertWithAmbiguitiesUtf8(input, 1, 3) }
        assertContentEquals(byteArrayOf(0x80.toByte(), 66), error.encodedContext)
        input[1] = 0
        error.encodedContext!![0] = 0
        assertContentEquals(byteArrayOf(0x80.toByte(), 66), error.encodedContext)
    }
}
