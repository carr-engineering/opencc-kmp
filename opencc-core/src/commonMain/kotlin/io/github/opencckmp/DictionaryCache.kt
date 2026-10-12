package io.github.opencckmp

/**
 * Host-owned immutable dictionary sharing. Implementations identify provider/resource/content/format,
 * serialize identical initialization, permit retry after failure and release only their own references.
 * Core depends on this contract alone. The optional opencc-cache module supplies LRU eviction,
 * SHA-256 identity and concurrent loading on every target.
 */
interface DictionaryCache : AutoCloseable {
    val capacity: Int
    val size: Int
    fun load(providerIdentity: Any, resourceName: String, bytes: ByteArray, reader: DictionaryReader): Dict
    fun clear()
}
