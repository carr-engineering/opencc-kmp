package io.github.opencckmp

import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class DictionaryConcurrencyTest {
    private fun await(latch: CountDownLatch) = assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent load timed out")

    @Test fun concurrentInitializationAndConversion() = sharedInitialization(fail = false)
    @Test fun concurrentFailureIsSharedAndRetrySucceeds() = sharedInitialization(fail = true)

    private fun sharedInitialization(fail: Boolean) {
        val cache = DictionaryCache(1)
        val provider = Any()
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val owner = AtomicReference<Thread>()
        val failure = InvalidFormat("first attempt")
        val reader = object : DictionaryReader {
            override val type = "test"
            override fun read(bytes: ByteArray): Dict {
                if (calls.incrementAndGet() == 1) {
                    owner.set(Thread.currentThread())
                    entered.countDown()
                    await(release)
                    if (fail) throw failure
                }
                return TextDict.fromBytes(bytes)
            }
        }
        val results = Array<AtomicReference<Result<Dict>>>(8) { AtomicReference() }
        val done = CountDownLatch(results.size)
        val threads = results.indices.map { id -> Thread {
            results[id].set(runCatching { cache.load(provider, "d", "汉\t漢\n".encodeToByteArray(), reader) })
            done.countDown()
        } }
        threads.forEach { it.start() }
        try {
            await(entered)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (threads.any { it !== owner.get() && it.state != Thread.State.BLOCKED } && System.nanoTime() < deadline) Thread.sleep(1)
            assertTrue(threads.all { it === owner.get() || it.state == Thread.State.BLOCKED }, "All callers must join the same initializer")
        } finally { release.countDown() }
        await(done)
        assertEquals(1, calls.get())
        if (fail) {
            results.forEach { assertSame(failure, it.get().exceptionOrNull()) }
            assertEquals(0, cache.size)
            assertEquals("漢", cache.load(provider, "d", "汉\t漢\n".encodeToByteArray(), reader).match("汉")?.defaultValue)
            assertEquals(2, calls.get())
        } else {
            val dictionary = results[0].get().getOrThrow()
            results.forEach { assertSame(dictionary, it.get().getOrThrow()) }
            val converter = SingleStageConverter(null, ConversionChain(listOf(Conversion(dictionary))))
            val outputs = Array(8) { AtomicReference<String>() }
            val workers = outputs.map { output -> Thread {
                repeat(200) { output.set(converter.convert("汉".repeat(100))) }
            } }
            workers.forEach { it.start() }
            workers.forEach { it.join(10_000); assertFalse(it.isAlive) }
            outputs.forEach { assertEquals("漢".repeat(100), it.get()) }
        }
    }

    @Test fun closeAndClearDoNotCancelExistingInitialization() {
        for (close in listOf(false, true)) {
            val cache = DictionaryCache(1)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val reader = object : DictionaryReader {
                override val type = "test"
                override fun read(bytes: ByteArray): Dict {
                    entered.countDown(); await(release)
                    return TextDict.fromBytes(bytes)
                }
            }
            val result = AtomicReference<Result<Dict>>()
            val thread = Thread { result.set(runCatching { cache.load(this, "d", "a\tA\n".encodeToByteArray(), reader) }) }
            thread.start()
            try {
                await(entered)
                if (close) cache.close() else cache.clear()
                assertEquals(0, cache.size)
            } finally { release.countDown() }
            thread.join(10_000)
            assertFalse(thread.isAlive)
            assertEquals("A", result.get().getOrThrow().match("a")?.defaultValue)
            assertEquals(0, cache.size)
        }
    }

    @Test fun digestMatchesJdkForBinaryPaddingBoundaries() {
        for (size in listOf(1, 55, 56, 63, 64, 65, 127, 128, 129, 1024, 1025, 1_000_000)) {
            val bytes = ByteArray(size) { (it * 23 + 71).toByte() }
            val expected = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(expected, ResourceDigest.sha256(bytes), "SHA-256 length $size")
        }
    }

    @Test fun pendingEntriesAreNotEvictedAndCompletedEntriesRemainBounded() {
        val cache = DictionaryCache(1)
        val provider = Any()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reader = object : DictionaryReader {
            override val type = "test"
            override fun read(bytes: ByteArray): Dict {
                if (bytes[0] == 'a'.code.toByte()) {
                    entered.countDown(); await(release)
                }
                return TextDict.fromBytes(bytes)
            }
        }
        val result = AtomicReference<Result<Dict>>()
        val thread = Thread { result.set(runCatching { cache.load(provider, "a", "a\tA\n".encodeToByteArray(), reader) }) }
        thread.start()
        val b: Dict
        try {
            await(entered)
            b = cache.load(provider, "b", "b\tB\n".encodeToByteArray(), reader)
            assertEquals(1, cache.size)
        } finally { release.countDown() }
        thread.join(10_000)
        assertFalse(thread.isAlive)
        assertEquals("A", result.get().getOrThrow().match("a")?.defaultValue)
        assertEquals(1, cache.size)
        assertSame(b, cache.load(provider, "b", "b\tB\n".encodeToByteArray(), reader))
    }
}
