package io.github.opencckmp.cli

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.*

class WindowsInplaceSafetyTest {
    private class Capture : CliOutput {
        val bytes = ByteArrayOutputStream()
        override fun write(bytes: ByteArray) { this.bytes.write(bytes) }
        override fun flush() = Unit
        override fun close() = Unit
    }

    @Test fun unavailableWindowsLinkCountHasTypedFailureAndPosixFallbackIsPreserved() {
        val unavailable: (java.nio.file.Path) -> Number = { throw UnsupportedOperationException("unix:nlink is unavailable") }
        val error = assertFailsWith<CliFailure> { jvmMultipleLinks("input.txt", true, unavailable) }
        assertContains(error.message.orEmpty(), "Native Windows CLI")
        assertFalse(jvmMultipleLinks("input.txt", false, unavailable))
        assertFalse(jvmMultipleLinks("input.txt", true) { 1 })
        assertTrue(jvmMultipleLinks("input.txt", true) { 2 })
    }

    @Test fun unknownWindowsLinkCountLeavesRealInputAndHardLinkUntouchedAndClosesInput() {
        val directory = Files.createTempDirectory("opencc-inplace-links-")
        try {
            val input = directory.resolve("input.txt")
            val alias = directory.resolve("alias.txt")
            val config = directory.resolve("config.json")
            val original = "汉字\n".toByteArray()
            Files.write(input, original)
            Files.createLink(alias, input)
            Files.writeString(config, """{"conversion_chain":[{"dict":{"type":"inline","entries":{"汉":"漢"}}}]}""")
            val delegate = JvmCliHost()
            val errors = Capture()
            var inputClosed = 0
            var inputReads = 0
            var linkChecks = 0
            val host = object : CliHost by delegate {
                override val windows = true
                override val error: CliOutput = errors
                override fun openInput(path: String): CliInput {
                    val stream = delegate.openInput(path)
                    if (path != input.toString()) return stream
                    return object : CliInput {
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            inputReads++
                            return stream.read(buffer, offset, length)
                        }
                        override fun close() { inputClosed++; stream.close() }
                    }
                }
                override fun multipleLinks(path: String): Boolean {
                    linkChecks++
                    return jvmMultipleLinks(path, windows) { throw UnsupportedOperationException("Unsupported attribute view unix") }
                }
                override fun temporaryNear(path: String): String = kotlin.error("Must reject before temporary creation")
                override fun openOutput(path: String): CliOutput = kotlin.error("Must reject before opening output")
                override fun preserveMetadata(source: String, target: String) = kotlin.error("Must reject before metadata mutation")
                override fun replace(source: String, target: String) = kotlin.error("Must reject before replacement")
                override fun remove(path: String) = kotlin.error("No temporary file should exist to remove")
            }
            val arguments = listOf("-c", config.toString(), "-i", input.toString(), "-o", input.toString())
            // Preserve the prior same-file error ordering: --in-place is checked before querying links.
            assertEquals(1, runCli(CliTool.Convert, arguments, host))
            assertEquals(0, linkChecks)
            assertContains(errors.bytes.toString("UTF-8"), "use --in-place")
            errors.bytes.reset()
            inputClosed = 0
            assertEquals(1, runCli(CliTool.Convert, arguments + "--in-place", host))
            assertEquals(1, linkChecks)
            assertEquals(1, inputClosed)
            assertEquals(0, inputReads)
            assertContains(errors.bytes.toString("UTF-8"), "cannot verify the file's hard-link count")
            assertContains(errors.bytes.toString("UTF-8"), "Native Windows CLI")
            assertContentEquals(original, Files.readAllBytes(input))
            assertContentEquals(original, Files.readAllBytes(alias))
            assertTrue(Files.isSameFile(input, alias))
            Files.list(directory).use { assertEquals(3L, it.count()) }
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
