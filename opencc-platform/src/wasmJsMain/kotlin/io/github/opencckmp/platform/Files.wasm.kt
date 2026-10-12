package io.github.opencckmp.platform

import io.github.opencckmp.*

actual fun readFileBytes(path: String): ByteArray = throw FileNotFound(path)
actual fun writeFileBytes(path: String, bytes: ByteArray): Unit = throw FileNotWritable(path)

internal actual fun resourceFileMetadata(path: String): String? = null
internal actual fun isAbsoluteResourcePath(path: String): Boolean = path.startsWith('/') || path.startsWith('\\')

