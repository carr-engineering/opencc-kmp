# Optional stored-ZIP resources

opencc-zip depends only on core and the Kotlin standard library. The complete aggregate and CLI
include it; ordinary core/platform/data consumers do not transitively download ZIP code.

```kotlin
import io.github.opencckmp.zip.ZipResourceProvider

val provider = ZipResourceProvider(archiveBytes, "resources.zip")
val config = provider.getResource("configs/s2t.json")
val converter = Config(provider).newFromFile(config.name)
```

The constructor copies the archive. getResource returns the resolved entry name, caller-owned bytes
and an opaque cacheKey. The key identifies one entry within this immutable archive snapshot and is
stable across reads. File timestamps and C++ allocation/owner mechanics are not copied into the API;
owned dictionary caches still use provider/resource identity and content digest. No mapped file or
file handle survives loading the bytes, and returned resources need no free/close calls.

Stored entries (compression method 0), 32-bit directory/offset fields and up to the pinned 16-bit
entry-count format are supported. Names normalize backslashes to slashes; unsafe/absolute/directory
entries are excluded, later duplicate names win and lookups fall back to a basename when appropriate.
resolve always reports FileNotFound because ZIP entries have no filesystem path, matching the pinned
provider. Invalid archives/entry layouts and unsupported compression produce InvalidFormat.

The pinned upstream provider does not decompress ZIP entries. The generated data transport bundles
use deflate compression to minimize downloads and are unpacked by build/host tooling. A runtime
--resource-zip archive must use stored entries. This preserves format capabilities while avoiding
an engine codec dependency; package/transport compression remains separate.

Portable tests verify lookup, ownership, config loading, malformed directories and compression/size
errors on JVM/JS/Wasm/macOS/iOS. The CLI process tests compare stored-ZIP conversion, inspection,
segmentation and ambiguity output directly with native OpenCC. Wider malformed-name/metadata/error
acceptance audits remain part of the final resource-contract inventory; no public release is claimed.
