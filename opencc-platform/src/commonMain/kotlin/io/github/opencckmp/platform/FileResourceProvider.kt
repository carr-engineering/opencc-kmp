package io.github.opencckmp.platform

import io.github.opencckmp.*

/** Searches caller-supplied directories in order. Core itself never accesses a filesystem. */
class FileResourceProvider(searchPaths: List<String> = listOf(".")) : ResourceProvider {
    private val paths = searchPaths.toList()
    override fun read(name: String): ByteArray {
        for (directory in paths) {
            val resolved = resolveResource(directory, name)
            try { return readFileBytes(resolved) } catch (_: FileNotFound) { /* Try next path. */ }
        }
        throw FileNotFound(name)
    }
}

expect fun readFileBytes(path: String): ByteArray
expect fun writeFileBytes(path: String, bytes: ByteArray)

