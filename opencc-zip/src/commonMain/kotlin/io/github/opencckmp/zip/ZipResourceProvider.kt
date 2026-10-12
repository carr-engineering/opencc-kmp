/* Adapted from OpenCC's stored ZIP resource reader. Apache-2.0. */
package io.github.opencckmp.zip

import io.github.opencckmp.*

/** A Kotlin-owned resource value; [bytes] is newly allocated and belongs to its recipient. */
class ZipResource(name: String, bytes: ByteArray, cacheKey: Any) : Resource(name, bytes, cacheKey)

/** Pinned OpenCC ZIP semantics: stored entries, normalized names, last duplicate and basename fallback. */
class ZipResourceProvider(bytes: ByteArray, private val archiveName: String = "archive") : ResourceMetadataProvider {
    private val archive = bytes.copyOf()
    private class Entry(val method: Int, val compressed: Int, val size: Int, val offset: Int)
    private val entries = mutableMapOf<String, Entry>()

    init {
        if (archive.size < 22) invalid("Invalid zip archive")
        var eocd = archive.size - 22
        val first = maxOf(0, archive.size - 65535 - 22)
        while (eocd >= first && number(eocd, 4) != 0x06054b50L) eocd--
        if (eocd < first) invalid("Invalid zip archive")
        val count = number(eocd + 10, 2).toInt()
        val centralSize = number(eocd + 12, 4)
        val centralOffset = number(eocd + 16, 4)
        if (centralOffset > archive.size || centralSize > archive.size - centralOffset) invalid("Invalid zip central directory")
        var position = centralOffset.toInt()
        repeat(count) {
            if (!range(position, 46) || number(position, 4) != 0x02014b50L) invalid("Invalid zip central directory")
            val method = number(position + 10, 2).toInt()
            val compressed = number(position + 20, 4)
            val size = number(position + 24, 4)
            val nameLength = number(position + 28, 2).toInt()
            val extra = number(position + 30, 2).toInt()
            val comment = number(position + 32, 2).toInt()
            val local = number(position + 42, 4)
            val recordLength = 46 + nameLength + extra + comment
            if (!range(position, recordLength)) invalid("Invalid zip central directory")
            val raw = UTF8Util.decode(archive, position + 46, position + 46 + nameLength)
            val name = normalize(raw)
            if (!absolute(raw) && name.isNotEmpty() && !name.endsWith('/') && safe(name)) {
                if (local > Int.MAX_VALUE || !range(local.toInt(), 30) || number(local.toInt(), 4) != 0x04034b50L) invalid("Invalid zip local header")
                val offset = local + 30 + number(local.toInt() + 26, 2) + number(local.toInt() + 28, 2)
                if (offset > archive.size || compressed > archive.size - offset) invalid("Invalid zip entry data")
                if (size > Int.MAX_VALUE) throw InvalidFormat("ZIP resource exceeds Kotlin array capacity: $name")
                entries[name] = Entry(method, compressed.toInt(), size.toInt(), offset.toInt())
            }
            position += recordLength
        }
    }
    override fun read(name: String): ByteArray {
        val entry = entries.getValue(selectEntry(name))
        return archive.copyOfRange(entry.offset, entry.offset + entry.size)
    }

    /** ZIP data has no filesystem path, matching upstream Resolve's failure capability. */
    override fun resolve(name: String): String = throw FileNotFound(name)

    override fun getResource(name: String): ZipResource {
        val key = selectEntry(name)
        val entry = entries.getValue(key)
        return ZipResource(key, archive.copyOfRange(entry.offset, entry.offset + entry.size), entry)
    }

    private fun selectEntry(name: String): String {
        if (absolute(name)) throw FileNotFound(name)
        val normalized = normalize(name)
        if (!safe(normalized)) throw FileNotFound(name)
        val key = when {
            entries.containsKey(normalized) -> normalized
            '/' in normalized && entries.containsKey(normalized.substringAfterLast('/')) -> normalized.substringAfterLast('/')
            else -> throw FileNotFound(normalized)
        }
        val entry = entries.getValue(key)
        if (entry.method != 0) throw InvalidFormat("Unsupported zip compression method for $key")
        if (entry.compressed != entry.size) throw InvalidFormat("Invalid stored zip entry size for $key")
        return key
    }
    private fun number(offset: Int, width: Int): Long {
        if (!range(offset, width)) invalid("Invalid zip archive")
        var result = 0L
        for (byte in 0 until width) result = result or ((archive[offset + byte].toLong() and 255) shl (byte * 8))
        return result
    }
    private fun range(offset: Int, count: Int): Boolean = offset >= 0 && count >= 0 && offset <= archive.size && count <= archive.size - offset
    private fun invalid(message: String): Nothing = throw InvalidFormat("$message: $archiveName")
    private fun normalize(name: String): String = name.replace('\\', '/').trimStart('/')
    private fun absolute(name: String): Boolean = name.startsWith('/') || name.startsWith('\\') || name.length > 1 && name[1] == ':'
    private fun safe(name: String): Boolean = name.isNotEmpty() && !absolute(name) && name.split('/').none { it.isEmpty() || it == "." || it == ".." }
}
