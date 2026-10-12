@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.opencckmp.platform

import io.github.opencckmp.*
import kotlinx.cinterop.*
import platform.posix.*
import platform.windows.WIN32_FILE_ATTRIBUTE_DATA
import platform.windows.GetFileAttributesExW
import platform.windows.GET_FILEEX_INFO_LEVELS
import platform.windows.FILE_ATTRIBUTE_DIRECTORY

internal actual fun isAbsoluteResourcePath(path: String): Boolean = path.startsWith('/') || path.startsWith('\\') ||
    path.length >= 3 && path[1] == ':' && (path[2] == '/' || path[2] == '\\')

internal actual fun resourceFileMetadata(path: String): String? = memScoped {
    val info = alloc<WIN32_FILE_ATTRIBUTE_DATA>()
    if (GetFileAttributesExW(path, GET_FILEEX_INFO_LEVELS.GetFileExInfoStandard, info.ptr) == 0 ||
        info.dwFileAttributes and FILE_ATTRIBUTE_DIRECTORY.toUInt() != 0u) return@memScoped null
    "$path\n${info.ftLastWriteTime.dwHighDateTime}.${info.ftLastWriteTime.dwLowDateTime}\n${info.nFileSizeHigh}.${info.nFileSizeLow}"
}

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

