package io.github.opencckmp.jieba

import io.github.opencckmp.*
import kotlin.coroutines.*
import kotlin.test.*

class ModelResourceTest {
    private val dictionary = "勇敢 10 n\n的 1 x\n士兵 10 n\n"
    private fun model(key: ByteArray = "未".encodeToByteArray()): ByteArray {
        val probabilities = "0 -3.14e100 -3.14e100 0\n" +
            "-3.14e100 0 -3.14e100 -3.14e100\n0 -3.14e100 -3.14e100 -3.14e100\n" +
            "-3.14e100 0 -3.14e100 -3.14e100\n0 -3.14e100 -3.14e100 -3.14e100\n"
        val emission = key + ":0,知:0,中:0,文:0,勇:0,敢:0,的:0,士:0,兵:0\n".encodeToByteArray()
        return probabilities.encodeToByteArray() + emission + emission + emission + emission
    }

    @Test fun encodedModelKeysAndOwnedInitializationPreserveRuneCapabilities() {
        for (key in listOf(byteArrayOf(0xc0.toByte(), 0xaf.toByte()), byteArrayOf(0xe4.toByte(), 0x41, 0x41),
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
            byteArrayOf(0xf5.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte()))) {
            val bytes = model(key)
            val segmenter = JiebaSegmentation.fromText(dictionary, bytes)
            val input = key + "未知".encodeToByteArray()
            val before = segmenter.segmentUtf8(input).map { it.toList() }
            bytes.fill(0)
            assertEquals(before, segmenter.segmentUtf8(input).map { it.toList() })
            assertTrue(before.isNotEmpty())
        }
    }

    @Test fun encodedDictionaryKeysAndUserProtectionMatchCanonicalAndRawInputRunes() {
        for (key in listOf(byteArrayOf(0xc0.toByte(), 0xaf.toByte()), byteArrayOf(0xe4.toByte(), 0x41, 0x41),
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
            byteArrayOf(0xf5.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte()), "𠀀".encodeToByteArray())) {
            val word = key + "未知".encodeToByteArray()
            val bytes = word + " 100 n\n".encodeToByteArray() + dictionary.encodeToByteArray()
            val segmenter = JiebaSegmentation.fromBytes(bytes, model())
            val result = segmenter.segmentUtf8(word)
            assertEquals(1, result.size)
            assertContentEquals(word, result[0])
            bytes.fill(0)
            assertContentEquals(word, segmenter.segmentUtf8(word)[0])
        }
        // An overlong user key for 未 must protect the same rune in canonical string input.
        val users = listOf(byteArrayOf(0xf0.toByte(), 0x86.toByte(), 0x9c.toByte(), 0xaa.toByte(), 10))
        val protected = JiebaSegmentation.fromBytes(dictionary.encodeToByteArray(), model(), users)
        assertEquals(listOf("未", "知中"), protected.segment("未知中").values)
        users[0].fill(0)
        assertEquals(listOf("未", "知中"), protected.segment("未知中").values)
        val skipped = JiebaSegmentation.fromBytes(dictionary.encodeToByteArray(), model(),
            listOf(byteArrayOf(0xff.toByte(), 10) + "未\n".encodeToByteArray()))
        assertEquals(protected.segment("未知中").values, skipped.segment("未知中").values)
    }

    @Test fun malformedHMMRowsReturnTypedErrorsInsteadOfTerminatingTheHost() {
        val rows = model().decodeToString().trimEnd().split('\n')
        for (text in listOf("", "# comments only\n", rows.dropLast(1).joinToString("\n"),
            rows.toMutableList().apply { this[0] = "0 0 0" }.joinToString("\n"),
            rows.toMutableList().apply { this[5] = "未:0,,知:0" }.joinToString("\n"),
            rows.toMutableList().apply { this[5] = "未知:0" }.joinToString("\n"))) {
            assertFailsWith<InvalidFormat> { JiebaSegmentation.fromText(dictionary, text) }
        }
        assertFailsWith<InvalidFormat> { JiebaSegmentation.fromText(dictionary, model(byteArrayOf(0xff.toByte()))) }
    }

    @Test fun synchronousAndAsynchronousFactoriesLoadRawModelsAndAcceptReleaseAuxiliaryFiles() {
        val config = """{"name":"custom","segmentation":{"type":"jieba","resources":{"dict_path":"dict.txt","model_path":"model.utf8","user_dict_path":"user.txt"}},"conversion_chain":[]}"""
        val resources = mapOf("config.json" to config.encodeToByteArray(), "dict.txt" to dictionary.encodeToByteArray(),
            "model.utf8" to model(byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte())),
            "user.txt" to byteArrayOf(), "idf.utf8" to byteArrayOf(), "stop_words.utf8" to byteArrayOf())
        val factories = mapOf("jieba" to JiebaSegmentationFactory())
        val sync = Config(MemoryResourceProvider(resources), segmentationFactories = factories).newFromFile("config.json")
        var prepared: Result<Converter>? = null
        suspend { prepareConfig("config.json", AsyncResourceProvider { resources[it]?.copyOf() ?: throw FileNotFound(it) },
            segmentationFactories = factories) }.startCoroutine(object : Continuation<Converter> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Converter>) { prepared = result }
        })
        val async = checkNotNull(prepared).getOrThrow()
        val input = byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()) + "未知".encodeToByteArray()
        assertContentEquals(sync.convertUtf8(input), async.convertUtf8(input))
        assertEquals(sync.segmentation!!.segmentUtf8(input).map { it.toList() },
            async.segmentation!!.segmentUtf8(input).map { it.toList() })
    }
}
