package io.github.opencckmp.jieba

import io.github.opencckmp.*
import kotlin.coroutines.*
import kotlin.test.*

class JiebaTest {
    private val dictionary = "勇敢 10 n\n的 1 x\n士兵 10 n\n的士 1 n\n"
    private val emission = "未:0,知:0,中:0,文:0,勇:0,敢:0,的:0,士:0,兵:0,汉:0,𠀀:0,𠮷:0"
    private val model = """
        # begin/end/middle/single probabilities
        0 -3.14e100 -3.14e100 0
        -3.14e100 0 -3.14e100 -3.14e100
        0 -3.14e100 -3.14e100 -3.14e100
        -3.14e100 0 -3.14e100 -3.14e100
        0 -3.14e100 -3.14e100 -3.14e100
        $emission
        $emission
        $emission
        $emission
    """.trimIndent()

    @Test fun weightedRouteHmmAsciiSeparatorsAndSupplementaryRanges() {
        val segmenter = JiebaSegmentation.fromText(dictionary, model)
        assertEquals(listOf("勇敢", "的", "士兵"), segmenter.segment("勇敢的士兵").values)
        assertEquals(listOf("未知", "中文", "𠀀𠮷"), segmenter.segment("未知中文𠀀𠮷").values)
        assertEquals(listOf("abc1.2", " ", "5G3.14", ".", "15", "，", "未知", "。", "\t"),
            segmenter.segment("abc1.2 5G3.14.15，未知。\t").values)
        assertEquals(listOf("abc", "", "XYZ"), segmenter.segment("abc\u0000XYZ").values)
        assertEquals(emptyList(), segmenter.segment("").values)
        // Construct malformed UTF-16 at runtime; a klib literal may replace an unpaired surrogate.
        assertFailsWith<InvalidUTF16> { segmenter.segment(CharArray(1) { 0xd800.toChar() }.concatToString()) }
    }
    @Test fun userOverrideAndSingleCharacterProtectionArePreserved() {
        val protected = JiebaSegmentation.fromText(dictionary, model, listOf("未\n勇敢 0 n\n"))
        assertEquals(listOf("未", "知中"), protected.segment("未知中").values)
        // Zero explicit user frequency is log(0), unlike metadata user_freq=0's default weight.
        assertEquals(listOf("勇敢"), JiebaSegmentation.fromText(dictionary, model).segment("勇敢").values)
        val merged = TextDict(Lexicon(listOf(DictEntry("勇敢", listOf("10", "n", "base")),
            DictEntry("的", listOf("1", "x", "base")), DictEntry("士兵", listOf("10", "n", "base")),
            DictEntry("的士", listOf("1", "n", "base")), DictEntry("未", listOf("0", "", "user_freq")))))
        assertEquals(protected.segment("未知中").values, JiebaSegmentation(merged, model).segment("未知中").values)
        assertFailsWith<InvalidFormat> { JiebaSegmentation(TextDict(Lexicon(listOf(DictEntry("字", listOf("tag"))))), model) }
    }
    @Test fun dictionarySearchHonours512ScalarLimitAndNativeByteDecoding() {
        val word = "汉".repeat(513)
        val segmenter = JiebaSegmentation.fromText("汉 1 n\n$word 100 n\n", model)
        assertTrue(segmenter.segment(word).all { UTF8Util.length(it) <= 2 })
        assertEquals(emptyList(), segmenter.segmentUtf8(byteArrayOf(0xe4.toByte(), 0xb8.toByte())))
        assertEquals(listOf(" "), segmenter.segmentUtf8(byteArrayOf(0x80.toByte(), 0xa0.toByte())).map { bytes ->
            if (bytes.contentEquals(byteArrayOf(0x80.toByte(), 0xa0.toByte()))) " " else "unexpected"
        })
    }
    @Test fun factoryFallbackAndActuallySuspendingPreparationRequireAuxiliaryClosure() {
        val resources = mapOf("config.json" to """{"name":"jieba","segmentation":{"type":"jieba","resources":{"dict_path":"jieba_dict/jieba_merged.ocd2","model_path":"jieba_dict/hmm_model.utf8"}},"conversion_chain":[]}""".encodeToByteArray(),
            "jieba_dict/jieba.dict.utf8" to dictionary.encodeToByteArray(), "jieba_dict/hmm_model.utf8" to model.encodeToByteArray(),
            "jieba_dict/user.dict.utf8" to "未\n".encodeToByteArray(), "jieba_dict/idf.utf8" to "字 1\n".encodeToByteArray(),
            "jieba_dict/stop_words.utf8" to "的\n".encodeToByteArray())
        val factory = JiebaSegmentationFactory()
        val sync = Config(MemoryResourceProvider(resources), segmentationFactories = mapOf("jieba" to factory)).newFromFile("config.json")
        val requests = mutableListOf<Pair<String, Continuation<ByteArray>>>()
        var preparedResult: Result<Converter>? = null
        suspend { prepareConfig("config", AsyncResourceProvider { name -> suspendCoroutine { requests.add(name to it) } },
            segmentationFactories = mapOf("jieba" to factory)) }.startCoroutine(object : Continuation<Converter> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Converter>) { preparedResult = result }
            })
        var answered = 0
        while (preparedResult == null) {
            assertTrue(answered < requests.size, "Preparation stalled")
            val (name, continuation) = requests[answered++]
            val bytes = resources[name]
            if (bytes == null) continuation.resumeWithException(FileNotFound(name)) else continuation.resume(bytes)
        }
        val prepared = checkNotNull(preparedResult).getOrThrow()
        assertEquals(sync.convert("勇敢的士兵未知中"), prepared.convert("勇敢的士兵未知中"))
        assertTrue(requests.any { it.first == "jieba_dict/idf.utf8" })
        assertTrue(requests.any { it.first == "jieba_dict/stop_words.utf8" })
        assertFailsWith<OpenCCException> { prepared.convert("汉\u0000字") }
        assertEquals("汉", UTF8Util.decode(prepared.convertUtf8("汉\u0000字".encodeToByteArray(), mode = EncodedInputMode.NullTerminated)))
        assertFailsWith<OpenCCException> { Config(MemoryResourceProvider(resources - "jieba_dict/idf.utf8"),
            segmentationFactories = mapOf("jieba" to factory)).newFromFile("config.json") }
    }
}
