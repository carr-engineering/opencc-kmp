package io.github.opencckmp.cli

import io.github.opencckmp.*
import io.github.opencckmp.platform.ClasspathResourceProvider
import java.io.*
import java.nio.file.*
import java.nio.file.attribute.PosixFileAttributes
import java.util.Locale

internal actual fun platformHost(): CliHost = JvmCliHost()
internal actual fun exitCli(status: Int): Nothing = kotlin.system.exitProcess(status)
internal actual fun platformArguments(arguments: Array<String>): List<String> = arguments.toList()
class JvmCliHost : CliHost {
    override val input: CliInput = Input(System.`in`, "stdin", false)
    override val output: CliOutput = Output(System.out, "stdout", false)
    override val error: CliOutput = Output(System.err, "stderr", false)
    override val interactive: Boolean get() = System.console()?.let { if (Runtime.version().feature() >= 22) it.isTerminal else true } ?: false
    override val windows: Boolean get() = System.getProperty("os.name").startsWith("Windows")
    override val bundledResources: ResourceProvider = ClasspathResourceProvider()
    override val executablePath: String? get() = System.getProperty("opencc.launcher")
    override fun environment(name: String): String? = System.getenv(name)
    override fun openInput(path: String): CliInput = try {
        if (!Files.isRegularFile(Path.of(path))) throw FileNotFound(path)
        Input(FileInputStream(path), path, true)
    } catch (_: IOException) { throw FileNotFound(path) }
    override fun openOutput(path: String): CliOutput = try { Output(FileOutputStream(path), path, true) } catch (_: IOException) { throw FileNotWritable(path) }
    override fun sameFile(first: String, second: String): Boolean = runCatching { Files.isSameFile(Path.of(first), Path.of(second)) }.getOrDefault(false)
    override fun realPath(path: String): String = runCatching { Path.of(path).toRealPath().toString() }.getOrDefault(path)
    override fun multipleLinks(path: String): Boolean = runCatching { (Files.getAttribute(Path.of(path), "unix:nlink") as Number).toInt() > 1 }.getOrDefault(false)
    override fun temporaryNear(path: String): String = try {
        val target = Path.of(path).toAbsolutePath()
        Files.createTempFile(target.parent, target.fileName.toString() + ".opencc-", ".tmp").toString()
    } catch (_: IOException) { throw FileNotWritable("temporary file") }
    override fun preserveMetadata(source: String, target: String) {
        if (windows) return
        try {
            val attrs = Files.readAttributes(Path.of(source), PosixFileAttributes::class.java)
            Files.setOwner(Path.of(target), attrs.owner())
            Files.getFileAttributeView(Path.of(target), java.nio.file.attribute.PosixFileAttributeView::class.java).setGroup(attrs.group())
            Files.setAttribute(Path.of(target), "unix:mode", (Files.getAttribute(Path.of(source), "unix:mode") as Number).toInt() and 4095)
        } catch (_: IOException) { throw FileNotWritable(target) }
    }
    override fun replace(source: String, target: String) {
        try { Files.move(Path.of(source), Path.of(target), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(Path.of(source), Path.of(target), StandardCopyOption.REPLACE_EXISTING) }
        catch (_: IOException) { throw FileNotWritable(target) }
    }
    override fun remove(path: String) { runCatching { Files.deleteIfExists(Path.of(path)) } }
    override fun formatNumber(value: Double): String {
        if (value.isNaN()) return "nan"
        if (value.isInfinite()) return if (value < 0) "-inf" else "inf"
        if (value == 0.0) return if (value.toBits() < 0) "-0" else "0"
        val decimal = java.math.BigDecimal(value).round(java.math.MathContext(6, java.math.RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val exponent = decimal.precision() - decimal.scale() - 1
        return if (exponent < -4 || exponent >= 6) decimal.movePointLeft(exponent).toPlainString() + "e" +
            (if (exponent < 0) "-" else "+") + kotlin.math.abs(exponent).toString().padStart(2, '0') else decimal.toPlainString()
    }
    private class Input(val stream: InputStream, val name: String, val owned: Boolean) : CliInput {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try { maxOf(0, stream.read(buffer, offset, length)) } catch (_: IOException) { throw FileNotFound(name) }
        override fun close() { if (owned) stream.close() }
    }
    private class Output(val stream: OutputStream, val name: String, val owned: Boolean) : CliOutput {
        override fun write(bytes: ByteArray) { try { stream.write(bytes) } catch (_: IOException) { throw FileNotWritable(name) } }
        override fun flush() { try { stream.flush() } catch (_: IOException) { throw FileNotWritable(name) } }
        override fun close() { if (owned) stream.close() else flush() }
    }
}
