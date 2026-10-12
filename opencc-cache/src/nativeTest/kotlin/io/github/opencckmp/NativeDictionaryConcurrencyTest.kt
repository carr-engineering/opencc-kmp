@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class, kotlin.native.concurrent.ObsoleteWorkersApi::class)
@file:Suppress("DEPRECATION")

package io.github.opencckmp

import kotlin.concurrent.atomics.AtomicInt
import kotlin.native.concurrent.*
import kotlin.test.*
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

class NativeDictionaryConcurrencyTest {
    private class LoadInput(val cache: DictionaryCache, val identity: Any, val reader: DictionaryReader, val started: AtomicInt)
    @Test fun nativeThreadsShareInitializationAndImmutableConversion() {
        val cache = DictionaryCache()
        val identity = Any()
        val started = AtomicInt(0)
        val loads = AtomicInt(0)
        val release = AtomicInt(0)
        val reader = object : DictionaryReader {
            override val type = "text"
            override fun read(bytes: ByteArray): Dict {
                loads.addAndFetch(1)
                while (release.load() == 0) Worker.current.park(1_000, false)
                return TextDict.fromBytes(bytes)
            }
        }
        val workers = List(4) { Worker.start() }
        try {
            val futures = workers.map { worker -> worker.execute(TransferMode.SAFE, { LoadInput(cache, identity, reader, started) }) { input ->
                input.started.addAndFetch(1)
                input.cache.load(input.identity, "d", "汉\t漢\n".encodeToByteArray(), input.reader)
            } }
            val deadline = TimeSource.Monotonic.markNow()
            try {
                while ((started.load() < workers.size || loads.load() == 0) && deadline.elapsedNow() < 10.seconds) Worker.current.park(1_000, false)
                assertEquals(workers.size, started.load())
                assertEquals(1, loads.load())
            } finally { release.store(1) }
            val dictionaries = futures.map { it.result }
            dictionaries.forEach { assertSame(dictionaries[0], it) }
            assertEquals(1, loads.load())
            val converter = SingleStageConverter(null, ConversionChain(listOf(Conversion(dictionaries[0]))))
            val outputs = workers.map { worker -> worker.execute(TransferMode.SAFE, { converter }) { converter ->
                var output = ""
                repeat(200) { output = converter.convert("汉".repeat(100)) }
                output
            } }
            outputs.forEach { assertEquals("漢".repeat(100), it.result) }
            cache.close()
            assertEquals("漢", converter.convert("汉"))
        } finally {
            release.store(1)
            workers.forEach { it.requestTermination().result }
        }
    }
}
