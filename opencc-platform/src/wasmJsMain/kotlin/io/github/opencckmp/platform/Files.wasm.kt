package io.github.opencckmp.platform

import io.github.opencckmp.*

actual fun readFileBytes(path: String): ByteArray = throw FileNotFound(path)
actual fun writeFileBytes(path: String, bytes: ByteArray): Unit = throw FileNotWritable(path)

