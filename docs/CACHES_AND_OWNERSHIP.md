# Resource identity, caching and ownership

Loaded standard dictionaries are immutable. Converters use per-call state and can be shared across
threads; streaming sessions, phrase-extraction sessions and C-style facade error contexts are
caller-owned mutable values. Serialize calls to each of those sessions. Returned strings and
candidate/list snapshots use ordinary Kotlin ownership and need no freeing.

`Config` shares referenced dictionaries within a single load. For repeated loads, explicitly own a
`DictionaryCache` and pass it to `Config`, `SimpleConverter` or `prepareConfig`:

```kotlin
val cache = DictionaryCache(capacity = 16)
val loader = Config(provider, dictionaryCache = cache)
val first = loader.newFromFile("s2t.json")
val second = loader.newFromFile("s2t.json")
cache.close()
check(first.convert("汉") == second.convert("汉")) // already-loaded dictionaries remain valid
```

Add `io.github.opencckmp:opencc-cache` for the `DictionaryCache(capacity)` factory,
`LruDictionaryCache` implementation, SHA-256 and verified resource wrappers. Core exposes the
`DictionaryCache` interface only, so engine clients do not download unused implementations.
`opencc-all` includes this module. It has no data, platform, network, coroutine or tooling dependency.

Identity includes provider identity, logical resource name, SHA-256 of the supplied bytes, format
name and reader instance identity. Provider and reader identities use referential equality, so
unrelated wrappers cannot accidentally share data. Standard readers are stable objects. Hosts may
pass an explicit stable `cacheIdentity` token to share across their own equivalent provider wrappers.
Different bytes under the same resource name always create a different entry. Changing custom
reader behavior requires a new reader instance. Both identity and bytes must represent the same
resource acquisition; providers remain responsible for safe concurrent reads and correct snapshots.

Concurrent callers share one initialization for an identical entry. A reader failure is shared
with callers waiting on that initialization and removed before the failing load returns; subsequent
loads retry. Custom readers must return immutable dictionaries and must not recursively load their
own cache entry. Different entries initialize independently. No networking, thread pool or coroutine
dependency is introduced into core.

The cache retains at most `capacity` completed entries, evicting the least recently requested ones.
Pending entries are not evicted and can temporarily exceed that limit. Capacity counts dictionaries,
not bytes; choose it for the host's workload. `size` counts retained completed successes. `clear()`
drops all current references, including pending ones. `close()` is idempotent, drops references and
rejects later cache loads/clear with `IllegalStateException`. Existing loads still finish for their
callers after clear/close, without repopulating a removed entry. Eviction, clearing, cache close and
closing an `OpenCCHandle` never invalidate another converter's dictionary. There is no global cache.

Resource acquisition still happens on a cache hit: bytes are snapshotted and hashed before lookup,
so updated local files and host downloads cannot return stale dictionaries. A cache saves decoding
and index construction, not network/file reads. Hosts can independently cache verified delivered
bytes. `prepareConfig` preserves its async provider's identity across the temporary memory snapshots
it creates, allowing repeated preparations to share loaded dictionaries.

`VerifiedResourceProvider` and `VerifiedAsyncResourceProvider` accept a copied map of expected
`ResourceIntegrity(size, sha256)` records. They reject unlisted resources, changed lengths and hash
mismatches before bytes reach the config/dictionary parser; failure is `ResourceVerificationFailed`.
Metadata must come from a host-trusted manifest. These wrappers verify delivered bytes, not the
manifest's authenticity. Hosts manage downloads and persistence; the optional module only verifies delivered bytes.
Use `ResourceDigest.sha256` for the same portable SHA-256 operation.

Portable tests cover identity, byte changes, reader changes, LRU eviction, retries, close/clear,
facade independence, async identity and verification. JVM tests use eight actual threads for
single initialization/failure sharing and concurrent conversion, and check close/clear during an
initialization plus pending-entry eviction. macOS/iOS Native tests use four worker threads with
the modern shared memory manager. SHA-256 tests include standard vectors and JVM/JDK comparisons
at padding boundaries through a million-byte binary input.

The module boundary is measured: an initial combined core implementation exceeded the engine's
dependency download allowance (183,502 bytes versus a 176,932-byte gate). Isolating implementations
restored the engine increment to 166,071 bytes; Android APK increment is 14,541 bytes. Selecting
the optional Android cache artifact downloads another 19,746 bytes in this build. The unchanged
Android, production JS/Wasm and installed Swift size gates all passed for the five existing profiles.
These profiles omit runtime cache selection; published test consumers separately exercise it.
`measurements/android-cache-isolation.json` records exact Android graphs, resource hashes and sizes.
