package io.github.opencckmp.platform

import io.github.opencckmp.*

/** Searches caller-supplied directories in order. Core itself never accesses a filesystem. */
class FileResourceProvider(searchPaths: List<String> = listOf(".")) : ResourceMetadataProvider {
    private val paths = searchPaths.toList()
    override fun read(name: String): ByteArray = readFileBytes(resolve(name))

    override fun resolve(name: String): String {
        if (name.isEmpty()) throw FileNotFound(name)
        if (isAbsoluteResourcePath(name)) {
            if (resourceFileMetadata(name) != null) return name
            throw FileNotFound(name)
        }
        for (directory in paths) {
            val candidate = when {
                directory.isEmpty() -> name
                directory.endsWith('/') || directory.endsWith('\\') -> directory + name
                else -> "$directory/$name"
            }
            if (resourceFileMetadata(candidate) != null) return candidate
        }
        throw FileNotFound("$name (searched: ${paths.joinToString(", ")})")
    }

    override fun getResource(name: String): Resource {
        val path = resolve(name)
        val bytes = readFileBytes(path)
        val key = resourceFileMetadata(path) ?: throw FileNotFound(path)
        return Resource(path, bytes, key)
    }
}

expect fun readFileBytes(path: String): ByteArray
expect fun writeFileBytes(path: String, bytes: ByteArray)

/** Regular-file freshness token; null for missing paths and other filesystem objects. */
internal expect fun resourceFileMetadata(path: String): String?
internal expect fun isAbsoluteResourcePath(path: String): Boolean

