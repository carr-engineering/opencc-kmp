@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
package io.github.opencckmp.cli

import io.github.opencckmp.*
import kotlinx.cinterop.*
import platform.posix.*

internal actual fun platformHost(): CliHost = NativeCliHost()
internal actual fun platformArguments(arguments: Array<String>): List<String> = argumentsNative(arguments)
internal expect fun openNative(path: String, mode: String): CPointer<FILE>?
internal expect fun interactiveNative(): Boolean
internal expect fun binaryNative()
internal expect fun sameNative(first: String, second: String): Boolean
internal expect fun realNative(path: String): String
internal expect fun linksNative(path: String): Boolean
internal expect fun temporaryNative(path: String): String
internal expect fun metadataNative(source: String, target: String)
internal expect fun replaceNative(source: String, target: String)
internal expect fun removeNative(path: String)
internal expect fun executableNative(): String?
internal expect fun argumentsNative(arguments: Array<String>): List<String>
internal expect fun environmentNative(name: String): String?

private class NativeCliHost : CliHost {
    init { binaryNative() }
    override val input: CliInput = Input(stdin!!, "stdin", false)
    override val output: CliOutput = Output(stdout!!, "stdout", false)
    override val error: CliOutput = Output(stderr!!, "stderr", false)
    override val interactive: Boolean get() = interactiveNative()
    override val windows: Boolean get() = kotlin.native.Platform.osFamily == kotlin.native.OsFamily.WINDOWS
    override val bundledResources: ResourceProvider? = null
    override val executablePath: String? get() = executableNative()
    override fun environment(name: String): String? = environmentNative(name)
    override fun openInput(path: String): CliInput = Input(openNative(path, "rb") ?: throw FileNotFound(path), path, true)
    override fun openOutput(path: String): CliOutput = Output(openNative(path, "wb") ?: throw FileNotWritable(path), path, true)
    override fun sameFile(first: String, second: String): Boolean = sameNative(first, second)
    override fun realPath(path: String): String = realNative(path)
    override fun multipleLinks(path: String): Boolean = linksNative(path)
    override fun temporaryNear(path: String): String = temporaryNative(path)
    override fun preserveMetadata(source: String, target: String) = metadataNative(source, target)
    override fun replace(source: String, target: String) = replaceNative(source, target)
    override fun remove(path: String) = removeNative(path)
    override fun formatNumber(value: Double): String = memScoped {
        val buffer = allocArray<ByteVar>(256)
        snprintf(buffer, 256.convert(), "%.6g", value)
        buffer.toKString()
    }
    private class Input(val file: CPointer<FILE>, val name: String, val owned: Boolean) : CliInput {
        private var closed = false
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            check(!closed)
            require(offset >= 0 && length >= 0 && offset <= buffer.size && length <= buffer.size - offset)
            if (length == 0) return 0
            val count = buffer.usePinned { fread(it.addressOf(offset), 1.convert(), length.convert(), file).toInt() }
            if (count == 0 && ferror(file) != 0) throw FileNotFound(name)
            return count
        }
        override fun close() { if (owned && !closed) { closed = true; fclose(file) } }
    }
    private class Output(val file: CPointer<FILE>, val name: String, val owned: Boolean) : CliOutput {
        private var closed = false
        override fun write(bytes: ByteArray) {
            check(!closed)
            if (bytes.isNotEmpty()) bytes.usePinned { if (fwrite(it.addressOf(0), 1.convert(), bytes.size.convert(), file).toInt() != bytes.size) throw FileNotWritable(name) }
        }
        override fun flush() { if (!closed && fflush(file) != 0) throw FileNotWritable(name) }
        override fun close() { if (owned && !closed) { closed = true; fclose(file) } else flush() }
    }
}
