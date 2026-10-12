package io.github.opencckmp

/** Immutable resource snapshot. [bytes] returns a caller-owned copy; no manual release is needed. */
open class Resource(val name: String, bytes: ByteArray, val cacheKey: Any) {
    private val content = bytes.copyOf()
    val size: Int get() = content.size
    val bytes: ByteArray get() = content.copyOf()
}

/** Optional metadata operations. Keeping these separate preserves the minimal [ResourceProvider] SAM. */
interface ResourceMetadataProvider : ResourceProvider {
    /** Resolve a filesystem path, or throw [FileNotFound] when this provider has no such path. */
    fun resolve(name: String): String
    fun getResource(name: String): Resource
}

/** Resolve through providers that support filesystem paths. Logical-only providers throw [FileNotFound]. */
fun ResourceProvider.resolve(name: String): String =
    (this as? ResourceMetadataProvider)?.resolve(name) ?: throw FileNotFound(name)

/** Obtain an owned snapshot, retaining provider-specific names and cache identities when available. */
fun ResourceProvider.getResource(name: String): Resource {
    if (this is ResourceMetadataProvider) return getResource(name)
    val bytes = read(name)
    return Resource(name, bytes, ContentResourceKey(this, name, bytes))
}

private class ContentResourceKey(private val provider: ResourceProvider, private val name: String, bytes: ByteArray) {
    private val content = bytes.copyOf()
    override fun equals(other: Any?): Boolean = other is ContentResourceKey &&
        provider === other.provider && name == other.name && content.contentEquals(other.content)
    override fun hashCode(): Int = 31 * name.hashCode() + content.contentHashCode()
}
