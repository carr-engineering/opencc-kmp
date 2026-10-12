package io.github.opencckmp

import kotlin.test.*

class ResourceMetadataTest {
    @Test fun snapshotsOwnBytesAndGenericProviderIdentityTracksContent() {
        val original = byteArrayOf(1, 2, 3)
        val snapshot = Resource("entry", original, "key")
        original.fill(0)
        snapshot.bytes.fill(0)
        assertContentEquals(byteArrayOf(1, 2, 3), snapshot.bytes)
        assertEquals(3, snapshot.size)
        var content = byteArrayOf(1, 2)
        val provider = ResourceProvider { content }
        val first = provider.getResource("entry")
        assertEquals(first.cacheKey, provider.getResource("entry").cacheKey)
        assertNotEquals(first.cacheKey, ResourceProvider { content }.getResource("entry").cacheKey)
        assertNotEquals(first.cacheKey, provider.getResource("other").cacheKey)
        content[0] = 3
        assertNotEquals(first.cacheKey, provider.getResource("entry").cacheKey)
        assertContentEquals(byteArrayOf(1, 2), first.bytes)
        assertFailsWith<FileNotFound> { provider.resolve("entry") }
    }

    @Test fun genericOperationsDispatchMetadataProviderAndKeepErrors() {
        val snapshot = Resource("resolved", byteArrayOf(), "cache")
        val provider: ResourceProvider = object : ResourceMetadataProvider {
            override fun read(name: String): ByteArray = snapshot.bytes
            override fun resolve(name: String): String = "resolved/$name"
            override fun getResource(name: String): Resource = snapshot
        }
        assertSame(snapshot, provider.getResource("entry"))
        assertEquals("resolved/entry", provider.resolve("entry"))
        val missing = ResourceProvider { throw FileNotFound(it) }
        assertEquals("absent", assertFailsWith<FileNotFound> { missing.getResource("absent") }.resourceName)
    }
}
