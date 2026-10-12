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
internal actual fun realNative(path: String): String {
    val resolved = realpath(path, null) ?: return path
    return try { resolved.toKString() } finally { free(resolved) }
}
internal actual fun temporaryNative(path: String): String {
    val pattern = (path + ".opencc-XXXXXX").encodeToByteArray() + 0.toByte()
    return pattern.usePinned {
        val file = mkstemp(it.addressOf(0))
        if (file < 0) throw FileNotWritable("temporary file")
        close(file)
        it.addressOf(0).toKString()
    }
}
internal actual fun replaceNative(source: String, target: String) { if (rename(source, target) != 0) throw FileNotWritable(target) }
internal actual fun removeNative(path: String) { remove(path) }
