@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.opencckmp.platform

import io.github.opencckmp.*
import kotlinx.cinterop.*
import platform.posix.*

actual fun readFileBytes(path: String): ByteArray {
    val file = fopen(path, "rb") ?: throw FileNotFound(path)
    try {
        if (fseek(file, 0, SEEK_END) != 0) throw FileNotFound(path)
        val length = ftell(file)
        if (length < 0 || length > Int.MAX_VALUE || fseek(file, 0, SEEK_SET) != 0) throw InvalidFormat("Invalid resource file size: $path")
        val bytes = ByteArray(length.toInt())
        if (bytes.isNotEmpty()) bytes.usePinned {
            if (fread(it.addressOf(0), 1.convert(), bytes.size.convert(), file).toLong() != length) throw FileNotFound(path)
        }
        return bytes
    } finally { fclose(file) }
}

actual fun writeFileBytes(path: String, bytes: ByteArray) {
    val file = fopen(path, "wb") ?: throw FileNotWritable(path)
    try {
        if (bytes.isNotEmpty()) bytes.usePinned {
            if (fwrite(it.addressOf(0), 1.convert(), bytes.size.convert(), file).toInt() != bytes.size) throw FileNotWritable(path)
        }
        if (fflush(file) != 0) throw FileNotWritable(path)
    } finally { fclose(file) }
}

