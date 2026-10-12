@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.opencckmp

import kotlin.concurrent.atomics.AtomicReference

/**
 * Caller-owned cache of immutable dictionaries. Identity combines the provider identity token
 * (referential equality), resource name, SHA-256 content version and dictionary reader instance.
 * Initialization of an identical entry is shared across threads. Failures are shared with current
 * waiters and then removed, permitting a later retry. Custom readers must return immutable dictionaries
 * and must not recursively request their own entry from this cache.
 *
 * At most [capacity] completed entries are retained in least-recently-used order; in-flight loads
 * are never evicted and may temporarily exceed that bound. [clear] and [close] release cache references
 * without invalidating dictionaries held by converters or interrupting existing loads. New loads
 * after close fail. No resources are retained in a global cache.
 */
class LruDictionaryCache(override val capacity: Int = 32) : DictionaryCache {
    init { require(capacity > 0) { "Dictionary cache capacity must be positive" } }

    private class Entry(val provider: Any, val resource: String, val digest: String, val format: String, val reader: DictionaryReader, initialize: () -> Dict) {
        val result = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { runCatching(initialize) }
        fun matches(provider: Any, resource: String, digest: String, reader: DictionaryReader): Boolean =
            this.provider === provider && this.reader === reader && this.resource == resource && this.digest == digest && format == reader.type
    }
    private class State(val entries: List<Entry> = emptyList(), val closed: Boolean = false)
    private val state = AtomicReference(State())

    /** Completed successful entries currently owned by this cache; in-flight loads are excluded. */
    override val size: Int get() = state.load().entries.count { it.result.isInitialized() && it.result.value.isSuccess }

    override fun load(providerIdentity: Any, resourceName: String, bytes: ByteArray, reader: DictionaryReader): Dict {
        check(!state.load().closed) { "Dictionary cache is closed" }
        val snapshot = bytes.copyOf()
        val digest = ResourceDigest.sha256(snapshot)
        val entry: Entry
        while (true) {
            val current = state.load()
            check(!current.closed) { "Dictionary cache is closed" }
            val existing = current.entries.firstOrNull { it.matches(providerIdentity, resourceName, digest, reader) }
            val selected = existing ?: Entry(providerIdentity, resourceName, digest, reader.type, reader) { reader.read(snapshot) }
            val next = State(current.entries.filter { it !== selected } + selected)
            if (state.compareAndSet(current, next)) {
                entry = selected
                break
            }
        }
        val result = entry.result.value
        trim(if (result.isFailure) entry else null)
        return result.getOrThrow()
    }

    private fun trim(failed: Entry?) {
        while (true) {
            val current = state.load()
            if (current.closed) return
            val entries = current.entries.filter { it !== failed }.toMutableList()
            var completed = entries.count { it.result.isInitialized() }
            val iterator = entries.iterator()
            while (completed > capacity && iterator.hasNext()) {
                if (iterator.next().result.isInitialized()) {
                    iterator.remove()
                    completed--
                }
            }
            if (state.compareAndSet(current, State(entries))) return
        }
    }

    /** Drop retained and in-flight cache references. Existing calls still complete for their callers. */
    override fun clear() {
        while (true) {
            val current = state.load()
            check(!current.closed) { "Dictionary cache is closed" }
            if (state.compareAndSet(current, State())) return
        }
    }

    override fun close() {
        while (true) {
            val current = state.load()
            if (current.closed || state.compareAndSet(current, State(closed = true))) return
        }
    }
}

/** Create the standard caller-owned cache from the optional opencc-cache module. */
fun DictionaryCache(capacity: Int = 32): DictionaryCache = LruDictionaryCache(capacity)
