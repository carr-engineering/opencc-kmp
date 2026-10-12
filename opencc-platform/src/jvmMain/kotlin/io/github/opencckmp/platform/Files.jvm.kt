package io.github.opencckmp.platform

import io.github.opencckmp.*
import java.io.File
import java.io.IOException

actual fun readFileBytes(path: String): ByteArray = try {
    val file = File(path)
    if (!file.isFile) throw FileNotFound(path)
    file.readBytes()
} catch (_: IOException) { throw FileNotFound(path) }

actual fun writeFileBytes(path: String, bytes: ByteArray) {
    try { File(path).writeBytes(bytes) } catch (_: IOException) { throw FileNotWritable(path) }
}

class ClasspathResourceProvider(
    private val prefix: String = "opencc/",
    private val classLoader: ClassLoader? = ClasspathResourceProvider::class.java.classLoader,
) : ResourceProvider {
    override fun read(name: String): ByteArray = classLoader?.getResourceAsStream(prefix + name)?.use { it.readBytes() }
        ?: throw FileNotFound(name)
}
