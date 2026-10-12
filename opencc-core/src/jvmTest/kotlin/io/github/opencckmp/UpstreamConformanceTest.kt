package io.github.opencckmp

import java.io.File
import kotlin.test.*

/** Dedicated task: upstreamConformance. It requires the verified C++ oracle, never a native binding. */
class UpstreamConformanceTest {
    private val root = File(System.getProperty("opencc.projectRoot") ?: error("Run the upstreamConformance task"))
    private val source = File(root, ".cache/upstream/OpenCC-025f371dc76b598d77384fbdab90c937471844d8")
    private val data = File(root, ".cache/conformance/text")
    private val engines = mutableMapOf<String, Converter>()
    private fun engine(config: String): Converter = engines.getOrPut(config) {
        Config(ResourceProvider { name ->
            val file = File(data, name)
            if (!file.isFile) throw FileNotFound(name)
            file.readBytes()
        }).newFromFile("$config.json")
    }
    private fun binaryEngine(config: String): Converter = engines.getOrPut("binary:$config") {
        Config(ResourceProvider { name ->
            val file = if (name.endsWith(".json")) File(source, "data/config/$name")
                else File(root, ".cache/oracle/data/$name")
            if (!file.isFile) throw FileNotFound(name)
            file.readBytes()
        }).newFromFile("$config.json")
    }

    @Test fun allUpstreamCasePairs() {
        val cases = JsonParser(File(source, "test/testcases/testcases.json").readText()).parse() as JsonValue.Object
        var count = 0
        for (item in cases.array("cases").values) {
            val case = item as JsonValue.Object
            for ((config, expected) in case.obj("expected").members) {
                assertEquals((expected as JsonValue.StringValue).value, engine(config).convert(case.string("input")),
                    "${case.string("id")} / $config")
                assertEquals(expected.value, binaryEngine(config).convert(case.string("input")),
                    "${case.string("id")} / $config / upstream ocd2")
                count++
            }
        }
        assertTrue(count > 100, "Unexpectedly small upstream test inventory: $count")
        println("Upstream input/answer pairs verified: $count")
    }

    @Test fun allGoldenCorpusPairs() {
        val golden = File(source, "test/golden")
        var count = 0
        for (output in File(golden, "output").listFiles().orEmpty().filter { it.extension == "txt" }.sorted()) {
            val config = output.nameWithoutExtension.substringAfterLast('.')
            val input = File(golden, "input/" + output.nameWithoutExtension.substringBeforeLast('.') + ".txt")
            if (!input.exists() || !File(data, "$config.json").exists()) continue
            assertEquals(output.readText(), engine(config).convert(input.readText()), output.name)
            assertEquals(output.readText(), binaryEngine(config).convert(input.readText()), output.name + " / upstream ocd2")
            count++
        }
        assertTrue(count > 0, "No golden corpus pairs found")
        println("Upstream golden corpus pairs verified: $count")
    }

    @Test fun everyConfigAgainstNativeOracle() {
        val samples = listOf("汉字转换 简体繁體 香港臺灣 日本駅弁当", "头发干燥 里面复盘 软件鼠标内存条", "⿰龙马 神 \uD840\uDC00 😀 ASCII 123", "什么怎么这么那么多么老么")
        val input = samples.joinToString("\n", postfix = "\n")
        val inputFile = File(root, ".cache/conformance/differential-input.txt").apply { writeText(input) }
        var count = 0
        for (config in data.listFiles().orEmpty().filter { it.extension == "json" }.sorted()) {
            val output = File(root, ".cache/conformance/differential-output.txt")
            val process = ProcessBuilder(File(root, ".cache/oracle/src/tools/opencc").absolutePath,
                "-c", File(source, "data/config/${config.name}").absolutePath,
                "--path", File(root, ".cache/oracle/data").absolutePath,
                "-i", inputFile.absolutePath, "-o", output.absolutePath).redirectErrorStream(true).start()
            val messages = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), messages)
            assertEquals(output.readText(), engine(config.nameWithoutExtension).convert(input), config.name)
            assertEquals(output.readText(), binaryEngine(config.nameWithoutExtension).convert(input), config.name + " / upstream ocd2")
            count++
        }
        assertEquals(16, count)
        println("Configs compared against pinned native oracle: $count")
    }
}
