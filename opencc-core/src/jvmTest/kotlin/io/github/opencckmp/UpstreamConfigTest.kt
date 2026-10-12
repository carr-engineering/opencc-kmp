package io.github.opencckmp

import java.io.File
import kotlin.test.*

class UpstreamConfigTest {
    @Test fun pinnedJsonSchemaWarningsErrorsAndOptions() {
        val root = File(System.getProperty("opencc.projectRoot") ?: error("Run upstreamConformance"))
        val corpus = JsonParser(File(root, ".cache/conformance/config-cases.json").readText()).parse() as JsonValue.Object
        val mismatches = mutableListOf<String>()
        var count = 0
        for (value in corpus.array("cases").values) {
            val row = value as JsonValue.Object
            val json = row.string("json_hex").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val warnings = mutableListOf<ConfigWarning>()
            val provider = ResourceProvider { name ->
                if (name == "config.json") json.copyOf() else {
                    val file = File(root, ".cache/conformance/config/$name")
                    if (!file.isFile) throw FileNotFound(name)
                    file.readBytes()
                }
            }
            val options = ConfigLoadOptions((row["include_tofu"] as JsonValue.BooleanValue).value)
            val loader = Config(provider, warningSink = { warnings.add(it) })
            val result = runCatching {
                val converter = if (row.string("mode") == "file") loader.newFromFile("config.json", options)
                    else loader.newFromString(UTF8Util.decode(json), options = options)
                UTF8Util.encode(converter.convert("汉abc神里"))
            }
            val label = "${row.string("id")} / ${row.string("mode")} / tofu=${options.includeTofuRiskDictionaries}"
            val expectedWarnings = row.array("warnings").values.map { warning -> (warning as JsonValue.Object).let {
                Triple(it.string("path"), it.string("schema_path"), it.string("keyword"))
            } }
            val actualWarnings = warnings.map { Triple(it.path, it.schemaPath, it.keyword) }
            if (expectedWarnings != actualWarnings) mismatches.add("$label warnings: $expectedWarnings != $actualWarnings")
            if (row.string("status") == "OK") {
                val expected = row.string("output_hex").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                if (!result.isSuccess || !expected.contentEquals(result.getOrNull())) mismatches.add("$label expected success: ${result.exceptionOrNull()}")
            } else {
                val error = result.exceptionOrNull()
                val kind = when (error) {
                    is InvalidFormat -> "InvalidFormat"; is InvalidUTF8 -> "InvalidUTF8"; is FileNotFound -> "FileNotFound"
                    is ShouldNotBeHere -> "ShouldNotBeHere"; is OpenCCException -> "OpenCCException"; null -> "OK"; else -> error::class.simpleName
                }
                if (kind != row.string("error_kind")) mismatches.add("$label error: ${row.string("error_kind")} != $kind ($error)")
                if (kind == "InvalidFormat") {
                    val message = row.string("error_hex").chunked(2).map { it.toInt(16).toByte() }.toByteArray().decodeToString()
                        .replace("std::string", "string").replace("a bool:", "a boolean:")
                    // OS-specific binary plugin paths are mapped to the common extension boundary.
                    if (error!!.message != message && !error.message.orEmpty().contains("Unavailable segmentation extension")) {
                        mismatches.add("$label message: $message != ${error.message}")
                    }
                }
            }
            count++
        }
        assertTrue(count >= 3852)
        File(root, ".cache/conformance/config-differences.txt").writeText(mismatches.joinToString("\n"))
        assertTrue(mismatches.isEmpty(), "${mismatches.size} config differences:\n" + mismatches.take(40).joinToString("\n"))
        println("Native configuration syntax/schema/semantic cases compared: $count")
    }
}
