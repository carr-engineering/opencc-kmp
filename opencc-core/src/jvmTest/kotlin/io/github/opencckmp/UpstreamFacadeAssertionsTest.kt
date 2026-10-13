package io.github.opencckmp

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

/** Native façade and Bazel literal outputs, using explicit providers instead of cwd/global env. */
class UpstreamFacadeAssertionsTest {
    private val root = File(System.getProperty("opencc.projectRoot") ?: error("Run upstreamConformance"))
    private val source = File(root, ".cache/upstream/OpenCC-025f371dc76b598d77384fbdab90c937471844d8")
    private val provider = ResourceProvider { name ->
        val file = if (name.endsWith(".json")) File(source, "data/config/$name") else File(root, ".cache/oracle/data/$name")
        if (!file.isFile) throw FileNotFound(name)
        file.readBytes()
    }
    private val fixtureProvider = ResourceProvider { name ->
        val file = File(source, "test/config_test/$name")
        if (!file.isFile) throw FileNotFound(name)
        file.readBytes()
    }
    private val input = "燕燕于飞差池其羽之子于归远送于野"
    private val expected = "燕燕于飛差池其羽之子于歸遠送於野"
    @Test fun simpleConverterConvert() {
        assertEquals(expected, SimpleConverter("config_test.json", fixtureProvider).convert(input))
    }
    @Test fun simpleConverterMultithreading() {
        val threads = Executors.newFixedThreadPool(2)
        try {
            val tasks = threads.invokeAll((1..2).map { Callable { simpleConverterConvert() } })
            simpleConverterConvert(); tasks.forEach { it.get() }
        } finally { threads.shutdownNow() }
    }
    @Test fun simpleConverterCInterface() {
        val context = OpenCC(Config(fixtureProvider))
        val handle = assertNotNull(context.open("config_test.json"))
        val encoded = input.encodeToByteArray(); val value = assertNotNull(handle.convertUtf8(encoded, mode = EncodedInputMode.NullTerminated))
        assertContentEquals(expected.encodeToByteArray(), value); assertTrue(handle.close())
        // The owned return stays usable after close; GC replaces native free().
        assertEquals(expected, value.decodeToString())
        val buffered = assertNotNull(context.open("config_test.json")); val buffer = ByteArray(1024)
        val size = assertNotNull(buffered.convertUtf8To(encoded, buffer))
        assertEquals(expected.encodeToByteArray().size, size); assertEquals(expected, buffer.copyOfRange(0, size).decodeToString())
        assertTrue(buffered.close())
        val path = "/opencc/no/such/file/or/directory"
        assertNull(context.open(path)); assertEquals("$path not found or not accessible.", context.error())
    }
    @Test fun simpleConverterS2t() { assertEquals("簡化字測試", SimpleConverter("s2t", provider).convert("简化字测试")) }
    @Test fun simpleConverterT2s() {
        val c = SimpleConverter("t2s", provider)
        assertEquals("简化字测试", c.convert("簡化字測試")); assertEquals("𫝈", c.convert("㑮"))
    }
    @Test fun cInterfaceS2t() {
        val handle = assertNotNull(OpenCC(Config(provider)).open("s2t"))
        assertEquals("簡化字測試", assertNotNull(handle.convertUtf8("简化字测试".encodeToByteArray(), mode = EncodedInputMode.NullTerminated)).decodeToString())
        assertTrue(handle.close())
    }
    @Test fun cInterfaceT2s() {
        val handle = assertNotNull(OpenCC(Config(provider)).open("t2s"))
        assertEquals("简化字测试", assertNotNull(handle.convertUtf8("簡化字測試".encodeToByteArray(), mode = EncodedInputMode.NullTerminated)).decodeToString())
        assertEquals("𫝈", assertNotNull(handle.convertUtf8("㑮".encodeToByteArray(), mode = EncodedInputMode.NullTerminated)).decodeToString())
        assertTrue(handle.close())
    }
}
