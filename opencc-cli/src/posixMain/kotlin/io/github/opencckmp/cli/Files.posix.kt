@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.opencckmp.cli

import io.github.opencckmp.*
import kotlinx.cinterop.*
import platform.posix.*

internal actual fun openNative(path: String, mode: String): CPointer<FILE>? = fopen(path, mode)
internal actual fun interactiveNative(): Boolean = isatty(0) != 0
internal actual fun binaryNative() {}
internal actual fun argumentsNative(arguments: Array<String>): List<String> = arguments.toList()
internal actual fun environmentNative(name: String): String? = getenv(name)?.toKString()
internal actual fun sameNative(first: String, second: String): Boolean = memScoped {
    val a = alloc<stat>(); val b = alloc<stat>()
    stat(first, a.ptr) == 0 && stat(second, b.ptr) == 0 && a.st_dev == b.st_dev && a.st_ino == b.st_ino
}
internal actual fun realNative(path: String): String {
    val resolved = realpath(path, null) ?: return path
    return try { resolved.toKString() } finally { free(resolved) }
}
internal actual fun linksNative(path: String): Boolean = memScoped { val info = alloc<stat>(); stat(path, info.ptr) == 0 && info.st_nlink.toLong() > 1 }
internal actual fun temporaryNative(path: String): String {
    val pattern = (path + ".opencc-XXXXXX").encodeToByteArray() + 0.toByte()
    return pattern.usePinned {
        val file = mkstemp(it.addressOf(0))
        if (file < 0) throw FileNotWritable("temporary file")
        close(file)
        it.addressOf(0).toKString()
    }
}
internal actual fun metadataNative(source: String, target: String) = memScoped {
    val info = alloc<stat>()
    if (stat(source, info.ptr) != 0 || chown(target, info.st_uid, info.st_gid) != 0 || chmod(target, (info.st_mode.toUInt() and 4095u).convert()) != 0) throw FileNotWritable(target)
}
internal actual fun replaceNative(source: String, target: String) { if (rename(source, target) != 0) throw FileNotWritable(target) }
internal actual fun removeNative(path: String) { remove(path) }
