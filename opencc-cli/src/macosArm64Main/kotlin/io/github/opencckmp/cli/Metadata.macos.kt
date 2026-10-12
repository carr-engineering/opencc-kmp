@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.opencckmp.cli

import io.github.opencckmp.*
import kotlinx.cinterop.*
import platform.posix.*

// stat fields differ in width between Darwin and Linux; keep their access in thin host adapters.
internal actual fun sameNative(first: String, second: String): Boolean = memScoped {
    val a = alloc<stat>(); val b = alloc<stat>()
    stat(first, a.ptr) == 0 && stat(second, b.ptr) == 0 && a.st_dev == b.st_dev && a.st_ino == b.st_ino
}
internal actual fun linksNative(path: String): Boolean = memScoped {
    val info = alloc<stat>(); stat(path, info.ptr) == 0 && info.st_nlink.toLong() > 1
}
internal actual fun metadataNative(source: String, target: String) = memScoped {
    val info = alloc<stat>()
    if (stat(source, info.ptr) != 0 || chown(target, info.st_uid, info.st_gid) != 0 ||
        chmod(target, (info.st_mode.toUInt() and 4095u).convert()) != 0) throw FileNotWritable(target)
}
