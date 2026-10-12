@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.opencckmp.platform

import io.github.opencckmp.*
import kotlinx.cinterop.*
import platform.posix.*

// Windows uses UTF-16 paths at the platform boundary; data remains byte-oriented.
actual fun readFileBytes(path: String): ByteArray = memScoped {
    val file = _wfopen(path.wcstr.ptr, "rb".wcstr.ptr) ?: throw FileNotFound(path)
    try {
        if (fseek(file, 0, SEEK_END) != 0) throw FileNotFound(path)
        val length = ftell(file)
        if (length < 0 || fseek(file, 0, SEEK_SET) != 0) throw InvalidFormat("Invalid resource file size: $path")
        val bytes = ByteArray(length)
        if (bytes.isNotEmpty()) bytes.usePinned {
            if (fread(it.addressOf(0), 1.convert(), bytes.size.convert(), file).toInt() != length) throw FileNotFound(path)
        }
        bytes
    } finally { fclose(file) }
}

actual fun writeFileBytes(path: String, bytes: ByteArray): Unit = memScoped {
    val file = _wfopen(path.wcstr.ptr, "wb".wcstr.ptr) ?: throw FileNotWritable(path)
    try {
        if (bytes.isNotEmpty()) bytes.usePinned {
            if (fwrite(it.addressOf(0), 1.convert(), bytes.size.convert(), file).toInt() != bytes.size) throw FileNotWritable(path)
        }
        if (fflush(file) != 0) throw FileNotWritable(path)
    } finally { fclose(file) }
}

