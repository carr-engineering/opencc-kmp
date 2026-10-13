package io.github.opencckmp.platform

import io.github.opencckmp.*
import io.github.opencckmp.zip.ZipResourceProvider
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class UpstreamFileConfigTest {
    private fun hex(value: String): String = value.encodeToByteArray().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    @Test fun filesystemAndZipConfigProviderCasesMatchPinnedReference() {
        val root = File(System.getProperty("opencc.projectRoot"))
        val probe = root.resolve(".cache/probes/config_resource_probe")
        assertTrue(probe.isFile)
        val directory = Files.createTempDirectory("opencc-native-config-").toFile()
        try {
            val configs = directory.resolve("config").apply { mkdir() }
            val first = directory.resolve("first").apply { mkdir() }
            val second = directory.resolve("second").apply { mkdir() }
            val cwd = directory.resolve("cwd").apply { mkdir() }
            first.resolve("dict.txt").writeText("鼠标\t第一\n")
            second.resolve("dict.txt").writeText("鼠标\t第二\n")
            val external = configs.resolve("external.json").apply { writeText(singleFileConfig("dict.txt")) }
            val adjacent = configs.resolve("adjacent.json").apply { writeText(singleFileConfig("adjacent.txt")) }
            configs.resolve("adjacent.txt").writeText("鼠标\t滑鼠\n")
            val parent = configs.resolve("parent.json").apply { writeText(singleFileConfig("../first/dict.txt")) }
            first.resolve("config.json").writeText(singleFileConfig("dict.txt"))
            cwd.resolve("config.json").writeText(inlineFileConfig("甲"))
            val zipPath = directory.resolve("resources.zip").apply {
                writeBytes(storedConfigZip(mapOf("config.json" to inlineFileConfig("乙"), "dict.txt" to "鼠标\t滑鼠\n")))
            }
            val absolute = configs.resolve("config.json").apply { writeText(inlineFileConfig("甲")) }
            val risk = configs.resolve("risk.json").apply { writeText("""{"name":"risk","conversion_chain":[{"dict":{"type":"inline","entries":{"鼠标":"甲"}}},{"dict":{"type":"text","may_output_tofu":true,"file":"missing.txt"}}]}""") }
            val unicode = configs.resolve("設定😀.json").apply { writeText(singleFileConfig("adjacent.txt")) }
            val environment = second.resolve("environment.json").apply { writeText(singleFileConfig("dict.txt")) }

            data class Case(val name: String, val kind: String, val paths: List<String> = emptyList(),
                            val tofu: Boolean = true, val env: String? = null)
            val cases = mutableListOf(
                Case(adjacent.path, "none"), Case(parent.path, "none"), Case(unicode.path, "none"),
                Case(external.path, "file", listOf(first.path, second.path)),
                Case(external.path, "file", listOf(directory.resolve("missing").path, second.path)),
                Case("config.json", "file", listOf(first.path)),
                Case("config.json", "zip", listOf(zipPath.path)),
                Case(absolute.path, "zip", listOf(zipPath.path)),
                Case(external.path, "zip", listOf(zipPath.path)),
                Case(risk.path, "file", listOf(first.path), false),
                Case(risk.path, "file", listOf(first.path), true),
                Case(configs.resolve("missing.json").path, "file", listOf(first.path)),
                Case(configs.path, "none"),
                Case(environment.name, "none", env = second.path),
            )
            if (File.separatorChar == '/') {
                first.resolve("literal\\name").writeText("鼠标\t伺服器\n")
                val literal = configs.resolve("literal.json").apply { writeText(singleFileConfig("literal\\name")) }
                cases += Case(literal.path, "file", listOf(first.path))
            }
            for (case in cases) {
                val inputs = listOf("鼠标", "a鼠标😀", "")
                val process = ProcessBuilder(listOf(probe.path, case.name, case.kind, case.tofu.toString()) + case.paths)
                    .directory(cwd).redirectErrorStream(false).apply {
                        if (case.env != null) environment()["OPENCC_DATA_DIR"] = case.env else environment().remove("OPENCC_DATA_DIR")
                    }.start()
                process.outputStream.bufferedWriter().use { writer -> inputs.forEach { writer.write(hex(it) + "\n") } }
                val expected = process.inputStream.bufferedReader().readLines()
                val diagnostics = process.errorStream.bufferedReader().readText()
                assertEquals(0, process.waitFor(), diagnostics)
                val provider = when (case.kind) {
                    "file" -> FileResourceProvider(case.paths)
                    "zip" -> ZipResourceProvider(zipPath.readBytes())
                    else -> null
                }
                val loader = FileConfigLoader(provider, dataDirectory = case.env)
                val actual = try {
                    val converter = loader.newFromFile(case.name, ConfigLoadOptions(case.tofu))
                    inputs.map { "OK\t${hex(converter.convert(it))}" }
                } catch (failure: OpenCCException) {
                    val kind = when (failure) { is FileNotFound -> "FileNotFound"; is InvalidFormat -> "InvalidFormat"; else -> "OpenCCException" }
                    listOf("ERROR\t$kind\t${hex(failure.message!!)}")
                }
                assertEquals(expected, actual, case.toString())
            }
        } finally { directory.deleteRecursively() }
    }
}
