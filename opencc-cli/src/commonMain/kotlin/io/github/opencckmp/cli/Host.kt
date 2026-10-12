package io.github.opencckmp.cli

import io.github.opencckmp.ResourceProvider

interface CliInput : AutoCloseable {
    /** Return bytes read or zero at EOF; throw on an I/O failure. */
    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int
}
interface CliOutput : AutoCloseable {
    fun write(bytes: ByteArray)
    fun flush()
}
/** Platform I/O and filesystem identity stay outside the common tool implementation. */
interface CliHost {
    val input: CliInput
    val output: CliOutput
    val error: CliOutput
    val interactive: Boolean
    val windows: Boolean
    val bundledResources: ResourceProvider?
    val executablePath: String? get() = null
    fun environment(name: String): String?
    fun openInput(path: String): CliInput
    fun openOutput(path: String): CliOutput
    fun sameFile(first: String, second: String): Boolean
    fun realPath(path: String): String
    fun multipleLinks(path: String): Boolean
    fun temporaryNear(path: String): String
    fun preserveMetadata(source: String, target: String)
    fun replace(source: String, target: String)
    fun remove(path: String)
    fun formatNumber(value: Double): String
}
internal expect fun platformHost(): CliHost
internal expect fun platformArguments(arguments: Array<String>): List<String>
internal fun CliHost.message(text: String) { error.write(text.encodeToByteArray()); error.flush() }
internal fun readAll(input: CliInput): ByteArray {
    val buffer = ByteArray(65536)
    var output = ByteArray(0)
    var size = 0
    while (true) {
        val count = input.read(buffer)
        if (count == 0) break
        if (count > Int.MAX_VALUE - size) throw CliFailure("Input exceeds Kotlin array capacity")
        if (size + count > output.size) output = output.copyOf(maxOf(size + count, minOf(Int.MAX_VALUE.toLong(), maxOf(65536L, output.size.toLong() * 2)).toInt()))
        buffer.copyInto(output, size, 0, count); size += count
    }
    return output.copyOf(size)
}
