package io.github.opencckmp.platform

import io.github.opencckmp.*
import io.github.opencckmp.zip.ZipResourceProvider
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

internal fun singleFileConfig(file: String): String =
    """{"name":"file","conversion_chain":[{"dict":{"type":"text","file":"${file.replace("\\", "\\\\").replace("\"", "\\\"")}"}}]}"""

internal fun inlineFileConfig(value: String): String =
    """{"name":"inline","conversion_chain":[{"dict":{"type":"inline","entries":{"鼠标":"$value"}}}]}"""

internal fun storedConfigZip(entries: Map<String, String>): ByteArray {
    val result = ByteArrayOutputStream()
    ZipOutputStream(result).use { zip ->
        for ((name, text) in entries) {
            val bytes = text.encodeToByteArray()
            val entry = ZipEntry(name).apply {
                method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size
                crc = CRC32().apply { update(bytes) }.value
            }
            zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
        }
    }
    return result.toByteArray()
}

class FileConfigLoaderTest {
    @Test fun filesystemConfigAndRootedDictionaryProvidersKeepSeparateLocations() {
        val directory = Files.createTempDirectory("opencc-file-config-").toFile()
        try {
            val config = directory.resolve("config").apply { mkdir() }.resolve("config.json")
            config.writeText(singleFileConfig("dict.txt"))
            val first = directory.resolve("first").apply { mkdir() }
            val second = directory.resolve("second").apply { mkdir() }
            first.resolve("dict.txt").writeText("鼠标\t第一\n")
            second.resolve("dict.txt").writeText("鼠标\t第二\n")
            val loader = FileConfigLoader(FileResourceProvider(listOf(first.path, second.path)))
            assertEquals("第一", loader.newFromFile(config.path).convert("鼠标"))
            first.resolve("dict.txt").delete()
            assertEquals("第二", loader.newFromFile(config.path).convert("鼠标"))
            assertEquals("第二", FileConfigLoader(FileResourceProvider(listOf(second.path)))
                .newFromString(config.readText(), config.parentFile.path).convert("鼠标"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun adjacentParentAndUnicodeConfigurationsLoadWithoutTransitiveData() {
        val directory = Files.createTempDirectory("opencc-配置😀-").toFile()
        try {
            val config = directory.resolve("設定😀.json").apply { writeText(singleFileConfig("dict.txt")) }
            directory.resolve("dict.txt").writeText("鼠标\t滑鼠\n")
            assertEquals("滑鼠", FileConfigLoader().newFromFile(config.path).convert("鼠标"))
            val nested = directory.resolve("nested").apply { mkdir() }.resolve("config.json")
            nested.writeText(singleFileConfig("../dict.txt"))
            assertEquals("滑鼠", FileConfigLoader().newFromFile(nested.path).convert("鼠标"))
            assertEquals("滑鼠", FileConfigLoader().newFromString(config.readText(), directory.path).convert("鼠标"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun providerConfigWinsAndAbsoluteHostConfigFallsBackWithZipResources() {
        val directory = Files.createTempDirectory("opencc-config-priority-").toFile()
        try {
            val host = directory.resolve("config.json").apply { writeText(inlineFileConfig("甲")) }
            val zip = ZipResourceProvider(storedConfigZip(mapOf("config.json" to inlineFileConfig("乙"),
                "dict.txt" to "鼠标\t滑鼠\n")))
            val loader = FileConfigLoader(zip)
            assertEquals("乙", loader.newFromFile("config.json").convert("鼠标"))
            assertEquals("甲", loader.newFromFile(host.path).convert("鼠标"))
            host.writeText(singleFileConfig("dict.txt"))
            assertEquals("滑鼠", loader.newFromFile(host.path).convert("鼠标"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun pluginsOptionsWarningsCustomReadersAndOwnedCacheSurviveTheAdapter() {
        val directory = Files.createTempDirectory("opencc-config-context-").toFile()
        try {
            val file = directory.resolve("config.json")
            var captured: SegmentationProperties? = null
            val warnings = mutableListOf<ConfigWarning>()
            var reads = 0
            val cache = object : DictionaryCache {
                override val capacity = 1
                override val size = 0
                override fun clear() = Unit
                override fun close() = Unit
                override fun load(providerIdentity: Any, resourceName: String, bytes: ByteArray, reader: DictionaryReader): Dict {
                    assertEquals("dict.custom", resourceName)
                    reads++
                    return reader.read(bytes)
                }
            }
            val custom = object : DictionaryReader {
                override val type = "custom"
                override fun read(bytes: ByteArray): Dict = TextDict.fromBytes(bytes)
            }
            val resources = MemoryResourceProvider(mapOf("dict.custom" to "鼠标\t滑鼠\n".encodeToByteArray()))
            file.writeText("""{"name":"context","segmentation":{"type":"capture"},"conversion_chain":[{"dict":{"type":"custom","file":"dict.custom"}},{"dict":{"type":"unknown","may_output_tofu":true,"file":5}}]}""")
            val factory = SegmentationFactory { properties, _ ->
                captured = properties
                MaxMatchSegmentation(TextDict(Lexicon(emptyList())))
            }
            val loader = FileConfigLoader(resources, readers = listOf(custom), segmentationFactories = mapOf("capture" to factory),
                warningSink = warnings::add, dictionaryCache = cache)
            assertEquals("滑鼠", loader.newFromFile(file.path, ConfigLoadOptions(false)).convert("鼠标"))
            assertEquals(file.parentFile.path + "/", captured?.get("__config_dir"))
            assertEquals(1, reads)
            assertTrue(warnings.isNotEmpty())
            val failure = assertFailsWith<FileNotFound> { loader.newFromFile(directory.resolve("missing.json").path) }
            assertEquals(directory.resolve("missing.json").path, failure.resourceName)
        } finally { directory.deleteRecursively() }
    }
}
