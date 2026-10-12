package io.github.opencckmp.zip

import io.github.opencckmp.*
import kotlin.test.*

class ZipResourceTest {
    private fun image(): ByteArray = """504b030414000000000051934c5d1fbe84d108000000080000000e00000064696374696f6e6172792e747874e6b18909e6bca20a504b030414000000000051934c5d678516f92400000024000000120000006e65737465642f636f6e6669672e6a736f6e7b226e616d65223a227a6970222c22636f6e76657273696f6e5f636861696e223a5b5d7d504b030414000000000051934c5d6e03cff206000000060000000a0000002e2e2f69676e6f726564756e73616665504b0102140314000000000051934c5d1fbe84d108000000080000000e000000000000000000000080010000000064696374696f6e6172792e747874504b0102140314000000000051934c5d678516f924000000240000001200000000000000000000008001340000006e65737465642f636f6e6669672e6a736f6e504b0102140314000000000051934c5d6e03cff206000000060000000a00000000000000000000008001880000002e2e2f69676e6f726564504b05060000000003000300b4000000b60000000000""".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun storedNamesFallbackAndOwnedResources() {
        val bytes = image()
        val provider = ZipResourceProvider(bytes)
        bytes.fill(0)
        val resource = provider.getResource("sub/dictionary.txt")
        assertEquals("dictionary.txt", resource.name)
        assertSame(resource.cacheKey, provider.getResource("dictionary.txt").cacheKey)
        assertNotSame(resource.cacheKey, ZipResourceProvider(image()).getResource("dictionary.txt").cacheKey)
        assertEquals("汉\t漢\n", UTF8Util.decode(resource.bytes))
        resource.bytes.fill(0)
        assertEquals("汉\t漢\n", UTF8Util.decode(provider.read("sub\\dictionary.txt")))
        assertFailsWith<FileNotFound> { provider.read("../ignored") }
        assertFailsWith<FileNotFound> { provider.read("/dictionary.txt") }
        assertFailsWith<FileNotFound> { provider.resolve("dictionary.txt") }
        assertEquals("x", Config(provider).newFromFile("nested/config.json").convert("x"))
    }
    @Test fun malformedDirectoryAndUnsupportedCompressionKeepTypedFailures() {
        assertFailsWith<InvalidFormat> { ZipResourceProvider(byteArrayOf(0)) }
        val bytes = image()
        val central = bytes.indices.first { i -> i + 4 <= bytes.size && bytes.copyOfRange(i, i + 4).contentEquals(byteArrayOf(0x50,0x4b,1,2)) }
        bytes[central + 10] = 8
        assertFailsWith<InvalidFormat> { ZipResourceProvider(bytes).read("dictionary.txt") }
        bytes[central + 10] = 0
        bytes[central + 20] = 1
        assertFailsWith<InvalidFormat> { ZipResourceProvider(bytes).read("dictionary.txt") }
    }
}
