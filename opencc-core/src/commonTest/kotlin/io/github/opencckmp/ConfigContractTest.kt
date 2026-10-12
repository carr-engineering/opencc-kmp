package io.github.opencckmp

import kotlin.test.*
import kotlin.coroutines.*

class ConfigContractTest {
    @Test fun warningsAreAdvisoryFirstViolationAndTypedPointers() {
        val warnings = mutableListOf<ConfigWarning>()
        val loader = Config(warningSink = { warnings.add(it) })
        assertEquals("x", loader.newFromString("""{"conversion_chain":[]}""").convert("x"))
        assertEquals(listOf("/conversion_chain"), warnings.map { it.path })
        assertEquals("minItems", warnings.single().keyword)
        assertEquals("/properties/conversion_chain", warnings.single().schemaPath)
        warnings.clear()
        loader.newFromString("""{"name":"x","conversion_chain":[{"dict":{"type":"group","dicts":[{"type":"inline","entries":{"a":"A"}}]}}]}""")
        assertEquals("/conversion_chain/0/dict", warnings.single().path)
        assertEquals("/definitions/dict", warnings.single().schemaPath)
        assertEquals("anyOf", warnings.single().keyword)
        warnings.clear()
        loader.newFromString("""{"name":"x","conversion_chain":[{"dict":{"type":"inline","entries":{"a":"A"}}}],"a/b~c":0}""")
        assertEquals("/a~1b~0c", warnings.single().path)
        assertEquals("additionalProperties", warnings.single().keyword)
    }
    @Test fun nativeNulPrefixesNumbersAndUnicodeEscapes() {
        val json = """{"name":"x","conversion_chain":[{"dict":{"type":"inline\u0000suffix","entries":{"a":"A"}}}]}"""
        assertEquals("A", Config().newFromString(json + '\u0000' + "ignored garbage").convert("a"))
        assertFailsWith<InvalidFormat> { Config().newFromString("""{"name":"x","conversion_chain":[],"unused":1e999}""") }
        assertEquals("x", Config().newFromString("""{"name":"x","conversion_chain":[],"unused":1e-999}""").convert("x"))
        for (escape in listOf("\\uD800", "\\uDC00", "\\uD800\\uD800", "\\u-001", "\\u+001")) {
            val error = assertFailsWith<InvalidFormat> { Config().newFromString("{\"name\":\"$escape\",\"conversion_chain\":[]}") }
            assertEquals("Invalid format: Error parsing JSON", error.message)
        }
        assertEquals("A", Config().newFromString("""{"name":"x","conversion_chain":[{"dict":{"type":"inline","entries":{"\uD840\uDC00":"A"}}}]}""").convert("𠀀"))
    }
    @Test fun pluginPropertiesValidateBeforeFactoryAndPreserveLogicalPrefixes() {
        var captured: SegmentationProperties? = null
        val loader = Config(segmentationFactories = mapOf("custom" to SegmentationFactory { properties, _ ->
            captured = properties
            MaxMatchSegmentation(TextDict(Lexicon(emptyList())))
        }))
        loader.newFromString("""{"name":"x","segmentation":{"type":"custom","resources":{"hmm":"model\u0000suffix","__config_dir":"override"},"hmm":"later","option":"value\u0000suffix"},"conversion_chain":[]}""")
        assertEquals("model", captured?.get("hmm"))
        assertEquals("value", captured?.get("option"))
        assertEquals("", captured?.get("__config_dir"))
        assertEquals(listOf("model", "later"), captured!!.orderedPairs.filter { it.first == "hmm" }.map { it.second })
        assertEquals(listOf("", "override"), captured.orderedPairs.filter { it.first == "__config_dir" }.map { it.second })
        val error = assertFailsWith<InvalidFormat> { Config().newFromString("""{"segmentation":{"type":"missing","resources":5},"conversion_chain":[]}""") }
        assertEquals("Invalid format: Property must be an object: resources", error.message)
    }
    @Test fun trulySuspendingPreparationLoadsOnlyRequiredResourcesOnce() {
        val requests = mutableListOf<Pair<String, Continuation<ByteArray>>>()
        val provider = AsyncResourceProvider { name -> suspendCoroutine { requests.add(name to it) } }
        var outcome: Result<Converter>? = null
        suspend { prepareConfig("c", provider, options = ConfigLoadOptions(false)) }.startCoroutine(object : Continuation<Converter> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Converter>) { outcome = result }
        })
        assertEquals(listOf("c.json"), requests.map { it.first })
        assertNull(outcome)
        val json = """{"name":"async","conversion_chain":[
          {"dict":{"type":"text","file":"d.txt"}},
          {"dict":{"type":"text","file":"d.txt"}},
          {"dict":{"type":"inline","entries":{"file":"literal"}}},
          {"dict":{"type":"unknown","may_output_tofu":true,"file":5}}
        ]}""" + '\u0000' + "ignored suffix"
        requests[0].second.resume(json.encodeToByteArray())
        assertEquals(listOf("c.json", "d.txt"), requests.map { it.first })
        assertNull(outcome)
        requests[1].second.resume("汉\t漢\n".encodeToByteArray())
        assertEquals("漢literal", assertNotNull(outcome).getOrThrow().convert("汉file"))
        assertEquals(2, requests.size)
    }
    @Test fun asynchronousErrorsKeepSynchronousLoadingOrder() {
        val json = """{"name":"order","segmentation":{"type":"mmseg","dict":{"type":"text","file":"missing.txt"}},"conversion_chain":false}"""
        var outcome: Result<Converter>? = null
        val warnings = mutableListOf<ConfigWarning>()
        suspend { prepareConfig("c", AsyncResourceProvider { name ->
            if (name == "c.json") json.encodeToByteArray() else throw FileNotFound(name)
        }, warningSink = { warnings.add(it) }) }.startCoroutine(object : Continuation<Converter> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Converter>) { outcome = result }
        })
        assertIs<FileNotFound>(assertNotNull(outcome).exceptionOrNull())
        assertEquals("/conversion_chain", warnings.single().path)
        assertEquals("type", warnings.single().keyword)
    }
    @Test fun pluginPreparationSupportsOptionDependentAndAdditionalResources() {
        val reads = mutableListOf<String>()
        val json = """{"name":"plugin","segmentation":{"type":"custom","resources":{"hmm":"missing-model"},"hmm_enabled":"false"},"conversion_chain":[]}"""
        val factory = object : SegmentationFactory {
            override fun resourcesToPrepare(properties: SegmentationProperties, declaredResources: List<String>): List<String> {
                assertEquals(listOf("missing-model"), declaredResources)
                return listOf("extra.txt")
            }
            override fun create(properties: SegmentationProperties, resources: ResourceProvider): Segmentation {
                assertEquals("extra", UTF8Util.decode(resources.read("extra.txt")))
                assertEquals("false", properties["hmm_enabled"])
                return MaxMatchSegmentation(TextDict(Lexicon(emptyList())))
            }
        }
        var outcome: Result<Converter>? = null
        suspend { prepareConfig("c", AsyncResourceProvider { name ->
            reads.add(name)
            when (name) { "c.json" -> json.encodeToByteArray(); "extra.txt" -> "extra".encodeToByteArray(); else -> throw FileNotFound(name) }
        }, segmentationFactories = mapOf("custom" to factory)) }.startCoroutine(object : Continuation<Converter> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Converter>) { outcome = result }
        })
        assertEquals("汉", assertNotNull(outcome).getOrThrow().convert("汉"))
        assertEquals(listOf("c.json", "extra.txt"), reads)
    }
}
