package io.github.opencckmp

/** Host-owned resource access. Missing resources must throw [FileNotFound]. No networking in core. */
fun interface ResourceProvider {
    fun read(name: String): ByteArray
}

fun interface AsyncResourceProvider {
    suspend fun read(name: String): ByteArray
}

/** Immutable snapshot; resource bytes are defensively copied on construction and retrieval. */
class MemoryResourceProvider(resources: Map<String, ByteArray>) : ResourceProvider {
    private val stored = resources.mapValues { it.value.copyOf() }
    override fun read(name: String): ByteArray = stored[name]?.copyOf() ?: throw FileNotFound(name)
}

/** Joins logical provider names without relying on platform filesystem classes. */
fun resolveResource(baseDirectory: String, name: String): String {
    val path = name.replace('\\', '/')
    if (baseDirectory.isEmpty() || path.startsWith('/') || (path.length > 1 && path[1] == ':')) return path
    return baseDirectory.trimEnd('/', '\\') + "/" + path
}

interface DictionaryReader {
    val type: String
    fun read(bytes: ByteArray): Dict
}

object TextDictionaryReader : DictionaryReader {
    override val type: String = "text"
    override fun read(bytes: ByteArray): Dict = TextDict.fromBytes(bytes)
}

/** Standard OpenCC runtime formats; dictionary builders and writers live in the tooling module. */
val standardDictionaryReaders: List<DictionaryReader>
    get() = listOf(TextDictionaryReader, DartsDictionaryReader, MarisaDictionaryReader)
