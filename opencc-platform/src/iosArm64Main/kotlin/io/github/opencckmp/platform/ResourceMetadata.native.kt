@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.opencckmp.platform

import kotlinx.cinterop.*
import platform.posix.*

// stat field widths differ across Native targets, so the adapter lives in each target leaf.
internal actual fun resourceFileMetadata(path: String): String? = memScoped {
    val info = alloc<stat>()
    if (stat(path, info.ptr) != 0 || info.st_mode.toInt() and 0xf000 != 0x8000) return@memScoped null
    "$path\n${info.st_mtimespec.tv_sec}.${info.st_mtimespec.tv_nsec}\n${info.st_size}"
}
