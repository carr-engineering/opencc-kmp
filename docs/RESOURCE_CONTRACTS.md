# Resource operations

The mapping targets OpenCC `ver.1.4.2`, commit
`025f371dc76b598d77384fbdab90c937471844d8`. It preserves resource operations and
observable lookup behaviour with Kotlin ownership. The engine still depends only
on the minimal `ResourceProvider.read(String): ByteArray` SAM.

| Upstream operation | Kotlin equivalent | Contract |
| --- | --- | --- |
| `ResourceProvider::Resolve` | `ResourceProvider.resolve(name)` extension; `ResourceMetadataProvider.resolve` | Return the selected filesystem path, or `FileNotFound` for providers without filesystem paths. |
| `ResourceProvider::GetResource` | `ResourceProvider.getResource(name)` extension; `ResourceMetadataProvider.getResource` | Return an immutable, owned `Resource` snapshot. Plain SAM providers return the requested logical name and a provider/name/content cache identity. |
| `Resource::Name` | `Resource.name` | Resolved filesystem path, selected ZIP entry name, or requested logical name. |
| `Resource::Data`, `Resource::Size` | `Resource.bytes`, `Resource.size` | Bytes are binary data, size is the byte count; every byte-array retrieval is a defensive copy. |
| `Resource::CacheKey` | `Resource.cacheKey: Any` | Opaque equality token identifying the resource version. Clients must compare tokens without interpreting their representation. |
| `Resource` ownership / `shared_ptr` | Kotlin object lifetime | Retaining the resource retains its bytes. There is no manual free or resource `close()`. |
| `FilesystemResourceProvider(searchPaths)` | `FileResourceProvider(searchPaths)` in `opencc-platform` | Ordered regular-file lookup, followed by one read of the selected file. |
| `ZipResourceProvider(zipFileName)` | `ZipResourceProvider(bytes, archiveName)` in `opencc-zip`; obtain bytes with a host provider or `readFileBytes` | Archive loading belongs to the host. The provider owns a defensive archive snapshot, accepts stored entries, and rejects unsupported compression with `InvalidFormat`. |

The metadata interface is optional so ordinary conversion does not acquire
filesystem, archive or metadata dependencies. Extensions dispatch to that
interface when available. A plain SAM's `getResource` reads synchronously and
propagates the provider's typed failures; `resolve` cannot fabricate a filesystem
path. Browser hosts prepare bytes asynchronously with `AsyncResourceProvider`
before synchronous conversion. Networking remains outside the engine.

## Filesystem lookup

An empty name fails immediately. Absolute names are checked independently of the
search-path list, including an empty list. A leading slash or backslash is treated
as absolute; Windows additionally accepts drive-letter names followed by a slash
or backslash. A POSIX drive-letter prefix remains relative.

Relative lookup preserves the name and roots literally. An empty root uses the
name unchanged; roots ending in either separator concatenate directly; other
roots join with `/`. This intentionally differs from `resolveResource`, which
joins logical configuration names portably. Directories and other non-regular
objects are skipped. Symbolic links to regular files are followed. The first
regular file wins, including when subsequently reading it fails: a read error does
not silently select a later file.

Missing absolute names report the name itself. Missing relative names report
`name (searched: root1, root2)` and retain empty roots. Filesystem resources use
the selected path as their name. Cache tokens include the path, modification time
and byte size. JVM and Native use high-resolution modification timestamps;
Android API 27+ uses nanoseconds, while API 23–26 uses the millisecond timestamp
available through its platform API. Timestamp representation and precision are
platform details, not public token serialization contracts. As upstream,
concurrent replacement between resolution, reading and metadata inspection is
not an atomic filesystem snapshot guarantee.

## ZIP lookup and lifetime

Entry names normalize backslashes to slashes. Unsafe paths, absolute names,
directory records and dot components are rejected or excluded. The last duplicate
entry wins. Lookup prefers the complete normalized name, then a basename fallback.
`resolve` always throws `FileNotFound`. `ZipResource` remains the concrete return
type and now inherits the common owned `Resource` contract, including `size`.

Repeated lookup of the same selected entry shares its opaque version token within
one provider. Tokens from independently constructed archive snapshots are distinct.
Returned bytes remain valid independently of the provider and cannot mutate the
archive or a previously returned snapshot. Copying is explicit at the resource
boundary; callers needing only conversion loading can keep using `read`.
