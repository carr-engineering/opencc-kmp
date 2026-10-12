package io.github.opencckmp

import kotlin.test.*
import kotlin.coroutines.*

class DictionaryCacheTest {
    private fun runSuspend(block: suspend () -> Unit) {
        var outcome: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { outcome = result }
        })
        // These providers return synchronously; no event loop or coroutine dependency is needed.
        assertNotNull(outcome).getOrThrow()
    }
    private class Reader(override val type: String = "text") : DictionaryReader {
        var loads = 0
        override fun read(bytes: ByteArray): Dict {
            loads++
            return TextDict.fromBytes(bytes)
        }
    }
    private val json = """{"name":"cache","conversion_chain":[{"dict":{"type":"text","file":"d.txt"}}]}"""

    @Test fun contentProviderResourceAndReaderIdentity() {
        val cache = DictionaryCache()
        val provider = Any()
        val reader = Reader()
        val bytes = "a\tA B\n".encodeToByteArray()
        val first = cache.load(provider, "d.txt", bytes, reader)
        assertSame(first, cache.load(provider, "d.txt", bytes.copyOf(), reader))
        assertNotSame(first, cache.load(Any(), "d.txt", bytes, reader))
        assertNotSame(first, cache.load(provider, "other.txt", bytes, reader))
        assertNotSame(first, cache.load(provider, "d.txt", bytes, Reader()))
        bytes[2] = 'C'.code.toByte()
        val updated = cache.load(provider, "d.txt", bytes, reader)
        assertNotSame(first, updated)
        assertEquals("A", first.match("a")?.defaultValue)
        assertEquals("C", updated.match("a")?.defaultValue)
        assertEquals(4, reader.loads)
        assertEquals(5, cache.size)
    }

    @Test fun leastRecentlyUsedEvictionClearAndIndependentLifetime() {
        val cache = DictionaryCache(2)
        val provider = Any()
        val reader = Reader()
        fun load(name: String) = cache.load(provider, name, "a\tA\n".encodeToByteArray(), reader)
        val a = load("a")
        val b = load("b")
        assertSame(a, load("a"))
        load("c")
        assertEquals(2, cache.size)
        assertNotSame(b, load("b"))
        cache.clear()
        assertEquals(0, cache.size)
        val current = load("a")
        assertNotSame(a, current)
        cache.close()
        cache.close()
        assertEquals(0, cache.size)
        assertEquals("A", a.match("a")?.defaultValue)
        assertEquals("A", current.match("a")?.defaultValue)
        assertFailsWith<IllegalStateException> { load("a") }
        assertFailsWith<IllegalStateException> { cache.clear() }
        assertFailsWith<IllegalArgumentException> { DictionaryCache(0) }
    }

    @Test fun failuresAreRemovedAndCanBeRetried() {
        val cache = DictionaryCache()
        val reader = object : DictionaryReader {
            override val type = "custom"
            var attempts = 0
            override fun read(bytes: ByteArray): Dict {
                if (++attempts == 1) throw InvalidFormat("transient")
                return TextDict.fromBytes(bytes)
            }
        }
        val provider = Any()
        val bytes = "a\tA\n".encodeToByteArray()
        assertFailsWith<InvalidFormat> { cache.load(provider, "d", bytes, reader) }
        assertEquals(0, cache.size)
        assertEquals("A", cache.load(provider, "d", bytes, reader).match("a")?.defaultValue)
        assertEquals(2, reader.attempts)
    }

    @Test fun configAndFacadeLoadsShareWithoutSharingHandleState() {
        val provider = MemoryResourceProvider(mapOf("c.json" to json.encodeToByteArray(), "d.txt" to "a\tA\n".encodeToByteArray()))
        val reader = Reader()
        val cache = DictionaryCache()
        val config = Config(provider, listOf(reader), dictionaryCache = cache)
        val first = config.newFromFile("c.json")
        val second = SimpleConverter("c", provider, readers = listOf(reader), dictionaryCache = cache)
        assertSame(first.conversionChain!!.conversions[0].dict, second.conversionChain!!.conversions[0].dict)
        val context = OpenCC(config)
        val closed = assertNotNull(context.open("c"))
        val survivor = assertNotNull(context.open("c"))
        closed.close()
        cache.close()
        assertNull(closed.convert("a"))
        assertEquals("A", survivor.convert("a"))
        assertEquals("A", first.convert("a"))
        assertEquals("A", second.convert("a"))
        assertEquals(1, reader.loads)
    }

    @Test fun asynchronousSnapshotsUseStableProviderIdentity() {
        runSuspend {
            val provider = AsyncResourceProvider { name -> when (name) {
                "c.json" -> json.encodeToByteArray()
                "d.txt" -> "a\tA\n".encodeToByteArray()
                else -> throw FileNotFound(name)
            } }
            val reader = Reader()
            val cache = DictionaryCache()
            val first = prepareConfig("c", provider, readers = listOf(reader), dictionaryCache = cache)
            val second = prepareConfig("c", provider, readers = listOf(reader), dictionaryCache = cache)
            assertSame(first.conversionChain!!.conversions[0].dict, second.conversionChain!!.conversions[0].dict)
            assertEquals(1, reader.loads)
        }
    }

    @Test fun immutableValuesDoNotExposeMutableIterators() {
        val entry = DictEntry("a", listOf("A"))
        val lexicon = Lexicon(listOf(entry, DictEntry("b", listOf("B"))))
        val iterator = lexicon.iterator()
        assertFalse(iterator is MutableIterator<*>)
        assertSame(entry, iterator.next())
        assertEquals(2, lexicon.size)
        assertFalse(Segments(listOf("a", "b")).iterator() is MutableIterator<*>)
    }

    @Test fun sha256KnownVectorsAndPaddingBoundaries() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ResourceDigest.sha256(byteArrayOf()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ResourceDigest.sha256("abc".encodeToByteArray()))
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", ResourceDigest.sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()))
    }

    @Test fun verifiedResourceDeliveryRejectsUnknownChangedAndTruncatedBytes() {
        val bytes = "a\tA\n".encodeToByteArray()
        val metadata = ResourceIntegrity(bytes.size.toLong(), ResourceDigest.sha256(bytes))
        val manifest = mutableMapOf("d" to metadata)
        val provider = VerifiedResourceProvider(ResourceProvider { bytes }, manifest)
        manifest.clear()
        val returned = provider.read("d")
        returned[0] = 'z'.code.toByte()
        assertContentEquals(bytes, provider.read("d"))
        assertFailsWith<ResourceVerificationFailed> { provider.read("unknown") }
        bytes[2] = 'B'.code.toByte()
        assertFailsWith<ResourceVerificationFailed> { provider.read("d") }
        val truncated = VerifiedResourceProvider(ResourceProvider { byteArrayOf() }, mapOf("d" to metadata))
        assertFailsWith<ResourceVerificationFailed> { truncated.read("d") }
        assertFailsWith<IllegalArgumentException> { ResourceIntegrity(-1, metadata.sha256) }
        assertFailsWith<IllegalArgumentException> { ResourceIntegrity(0, "bad hash") }
        runSuspend {
            val async = VerifiedAsyncResourceProvider(AsyncResourceProvider { "a\tA\n".encodeToByteArray() }, mapOf("d" to metadata))
            assertEquals("a\tA\n", UTF8Util.decode(async.read("d")))
            assertFailsWith<ResourceVerificationFailed> { async.read("missing") }
        }
    }
}
