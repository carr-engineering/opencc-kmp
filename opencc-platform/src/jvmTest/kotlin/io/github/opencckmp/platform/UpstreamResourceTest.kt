package io.github.opencckmp.platform

import io.github.opencckmp.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/** Compare actual public Kotlin operations against the pinned native ResourceProvider. */
class UpstreamResourceTest {
    private fun hex(value: ByteArray): String = value.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun hex(value: String): String = hex(value.encodeToByteArray())

    @Test fun filesystemOperationsMatchPinnedReference() {
        val root = File(System.getProperty("opencc.projectRoot"))
        val probe = root.resolve(".cache/probes/resource_probe")
        assertTrue(probe.isFile, "Run resourceConformance to prepare the verified reference probe")
        val directory = Files.createTempDirectory("opencc-native-resource-").toFile()
        try {
            val first = directory.resolve("first").apply { mkdir() }
            val second = directory.resolve("second").apply { mkdir() }
            val regular = first.resolve("汉😀.bin").apply { writeBytes(byteArrayOf(0, 1, 127, -1)) }
            first.resolve("entry").mkdir()
            second.resolve("entry").writeBytes(byteArrayOf(5))
            second.resolve("empty").writeBytes(byteArrayOf())
            val cases = mutableListOf(
                "汉😀.bin" to listOf(first.path, second.path),
                "entry" to listOf(first.path, second.path),
                "empty" to listOf(first.path, second.path),
                "汉😀.bin" to listOf(first.path + "/"),
                regular.absolutePath to emptyList(),
                regular.absolutePath to listOf("missing"),
                "" to listOf(first.path),
                "absent" to listOf(first.path, second.path),
                "absent" to emptyList(),
                "absent" to listOf("", first.path),
                first.absolutePath to emptyList(),
                directory.resolve("missing").absolutePath to emptyList(),
                "C:relative" to listOf(first.path),
                "C:/absolute" to emptyList(),
                "\\absolute" to emptyList(),
                File(".").absoluteFile.toPath().normalize().relativize(regular.toPath()).toString() to listOf(""),
            )
            if (File.separatorChar == '/') {
                first.resolve("literal\\name").writeBytes(byteArrayOf(6))
                cases += "literal\\name" to listOf(first.path)
                directory.resolve("root\\entry").writeBytes(byteArrayOf(9))
                cases += "entry" to listOf(directory.resolve("root\\").path)
                val link = first.toPath().resolve("link")
                Files.createSymbolicLink(link, regular.toPath())
                cases += "link" to listOf(first.path)
            }
            for ((name, roots) in cases) for (operation in listOf("resolve", "resource")) {
                val command = listOf(probe.path, operation, name) + roots
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                val expected = process.inputStream.bufferedReader().readText().trimEnd()
                assertEquals(0, process.waitFor(), command.toString())
                val provider: ResourceProvider = FileResourceProvider(roots)
                val actual = try {
                    if (operation == "resolve") "OK\t${hex(provider.resolve(name))}" else {
                        val resource = provider.getResource(name)
                        val sameKey = resource.cacheKey == provider.getResource(name).cacheKey
                        "OK\t${hex(resource.name)}\t${resource.size}\t${hex(resource.bytes)}\t${if (sameKey) 1 else 0}"
                    }
                } catch (failure: OpenCCException) {
                    "ERROR\t${hex(failure.message!!)}"
                }
                assertEquals(expected, actual, "$operation name=$name roots=$roots")
            }
        } finally { directory.deleteRecursively() }
    }
}
