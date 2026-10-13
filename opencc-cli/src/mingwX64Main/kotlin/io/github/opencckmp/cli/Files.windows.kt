@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.opencckmp.cli

import io.github.opencckmp.*
import kotlinx.cinterop.*
import platform.posix.*
import platform.windows.*

internal actual fun openNative(path: String, mode: String): CPointer<FILE>? = memScoped { _wfopen(path.wcstr.ptr, mode.wcstr.ptr) }
internal actual fun interactiveNative(): Boolean = _isatty(0) != 0
internal actual fun binaryNative() { _setmode(0, 0x8000); _setmode(1, 0x8000) }
private class FileIdentity(val volume: UInt, val high: UInt, val low: UInt, val links: UInt)
private fun identity(path: String): FileIdentity? = memScoped {
    val handle = CreateFileW(path, 0u, (FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE).toUInt(),
        null, OPEN_EXISTING.toUInt(), FILE_ATTRIBUTE_NORMAL.toUInt(), null)
    if (handle == INVALID_HANDLE_VALUE) return@memScoped null
    try {
        val value = alloc<BY_HANDLE_FILE_INFORMATION>()
        if (GetFileInformationByHandle(handle, value.ptr) == 0) null else FileIdentity(value.dwVolumeSerialNumber, value.nFileIndexHigh, value.nFileIndexLow, value.nNumberOfLinks)
    } finally { CloseHandle(handle) }
}
internal actual fun sameNative(first: String, second: String): Boolean {
    val a = identity(first) ?: return false
    val b = identity(second) ?: return false
    return a.volume == b.volume && a.high == b.high && a.low == b.low
}
internal actual fun linksNative(path: String): Boolean = (identity(path)?.links ?: 0u) > 1u
internal actual fun realNative(path: String): String = memScoped {
    val handle = CreateFileW(path, 0u, (FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE).toUInt(),
        null, OPEN_EXISTING.toUInt(), FILE_ATTRIBUTE_NORMAL.toUInt(), null)
    if (handle == INVALID_HANDLE_VALUE) return@memScoped path
    try {
        val flags = (FILE_NAME_NORMALIZED or VOLUME_NAME_DOS).toUInt()
        val required = GetFinalPathNameByHandleW(handle, null, 0u, flags)
        if (required == 0u || required >= Int.MAX_VALUE.toUInt()) return@memScoped path
        val capacity = required + 1u
        val output = allocArray<UShortVar>(capacity.toInt())
        val copied = GetFinalPathNameByHandleW(handle, output, capacity, flags)
        if (copied == 0u || copied >= capacity) path else output.toKStringFromUtf16()
    } finally { CloseHandle(handle) }
}
internal actual fun temporaryNative(path: String): String = memScoped {
    val slash = path.indexOfLast { it == '\\' || it == '/' }
    val parent = if (slash < 0) ".\\" else path.substring(0, slash + 1)
    val process = GetCurrentProcessId()
    val tick = GetTickCount64()
    repeat(100) { index ->
        val candidate = "$parent.opencc-$process-$tick-$index.tmp"
        val handle = CreateFileW(candidate, GENERIC_WRITE.toUInt(), 0u, null, CREATE_NEW.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(), null)
        if (handle != INVALID_HANDLE_VALUE) {
            try { return@memScoped candidate } finally { CloseHandle(handle) }
        }
    }
    throw FileNotWritable("temporary file")
}
internal actual fun metadataNative(source: String, target: String) {}
internal actual fun replaceNative(source: String, target: String) = memScoped {
    if (ReplaceFileW(target, source, null, 0u, null, null) == 0) throw FileNotWritable(target)
}
internal actual fun removeNative(path: String) { memScoped { _wremove(path.wcstr.ptr) } }
internal actual fun executableNative(): String? = memScoped {
    val output = allocArray<UShortVar>(32768)
    val count = GetModuleFileNameW(null, output, 32768u)
    if (count == 0u || count >= 32768u) null else output.toKStringFromUtf16()
}
internal actual fun environmentNative(name: String): String? = memScoped {
    val length = GetEnvironmentVariableW(name, null, 0u)
    if (length == 0u) return@memScoped null
    val output = allocArray<UShortVar>(length.toInt())
    if (GetEnvironmentVariableW(name, output, length) == 0u) null else output.toKStringFromUtf16()
}
internal actual fun argumentsNative(arguments: Array<String>): List<String> = memScoped {
    val count = alloc<IntVar>()
    val values = CommandLineToArgvW(GetCommandLineW()?.toKStringFromUtf16(), count.ptr) ?: return@memScoped arguments.toList()
    try { List(maxOf(0, count.value - 1)) { index -> values[index + 1]!!.toKStringFromUtf16() } }
    finally { LocalFree(values) }
}
