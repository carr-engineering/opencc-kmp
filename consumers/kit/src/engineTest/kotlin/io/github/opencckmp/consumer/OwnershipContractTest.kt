package io.github.opencckmp.consumer

import io.github.opencckmp.*
import kotlin.test.*

/** Resolves only published artifacts, including their common/Native/browser cache APIs. */
class OwnershipContractTest {
    @Test fun verifiedDeliveryAndCacheLifetimeAcrossPublishedApis() {
        val resources = mapOf(
            "test.json" to """{"name":"fixture","conversion_chain":[{"dict":{"type":"text","file":"test.txt"}}]}""".encodeToByteArray(),
            "test.txt" to "汉\t漢\n".encodeToByteArray(),
        )
        val manifest = resources.mapValues { (_, bytes) -> ResourceIntegrity(bytes.size.toLong(), ResourceDigest.sha256(bytes)) }
        val provider = VerifiedResourceProvider(MemoryResourceProvider(resources), manifest)
        val cache = DictionaryCache(2)
        val loader = Config(provider, dictionaryCache = cache)
        val first = loader.newFromFile("test.json")
        val second = SimpleConverter("test", provider, dictionaryCache = cache)
        assertSame(first.conversionChain!!.conversions[0].dict, second.conversionChain!!.conversions[0].dict)
        cache.close()
        assertEquals("漢", first.convert("汉"))
        assertEquals("漢", second.convert("汉"))
        assertContentEquals(byteArrayOf(0xe6.toByte()), first.convertUtf8Prefix("汉".encodeToByteArray(), 1))
        assertEquals("漢\u0000x", UTF8Util.decode(first.convertUtf8("汉\u0000x".encodeToByteArray())))
        val output = ByteArray(8)
        assertEquals(3, first.convertUtf8To("汉\u0000x".encodeToByteArray(), output))
        assertEquals("漢", UTF8Util.decode(output, 0, 3))
        assertEquals("漢", UTF8Util.decode(first.inspectUtf8("汉\u0000x".encodeToByteArray()).output))
        assertFailsWith<ResourceVerificationFailed> { provider.read("unlisted") }
    }
}
