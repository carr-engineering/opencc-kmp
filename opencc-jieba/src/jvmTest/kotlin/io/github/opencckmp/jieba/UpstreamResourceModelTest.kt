package io.github.opencckmp.jieba

import io.github.opencckmp.*
import java.io.File
import kotlin.test.*

/** Bounded custom model/resource audit; native process aborts are distinct from returned errors. */
class UpstreamResourceModelTest {
    private fun unhex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(value: ByteArray): String = value.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    @Test fun acceptedModelsAndPluginSchemaMatchAndNativeAbortsBecomeTypedErrors() {
        val root = File(System.getProperty("opencc.projectRoot"))
        val rows = root.resolve(".cache/conformance/jieba-resources/cases.tsv").readLines().map { it.split('\t') }
        val models = mutableMapOf<String, JiebaSegmentation>()
        val converters = mutableMapOf<String, Converter>()
        for (row in rows.filter { it[0] != "S" && it[0] != "C" }) {
            val name = row[0]
            val directory = File(row[1])
            fun model(): JiebaSegmentation {
                val dictionary = directory.resolve(row.getOrElse(4) { "dict.txt" })
                val bytes = dictionary.readBytes()
                val hmm = directory.resolve("model.utf8").readBytes()
                val users = listOf(directory.resolve("user.txt").readBytes())
                return if (dictionary.name.endsWith(".ocd2")) JiebaSegmentation.fromBytes(MarisaDict.fromBytes(bytes), hmm, users)
                    else JiebaSegmentation.fromBytes(bytes, hmm, users)
            }
            when (row[2]) {
                "ok" -> models[name] = model()
                "error" -> assertFailsWith<OpenCCException>(name) { model() }
                "abort" -> if (row[3] == "ok") {
                    // Empty auxiliary files assert only in the direct header build; release plugin accepts them.
                    models[name] = model()
                } else assertFailsWith<OpenCCException>(name) { model() }
            }
            val resources = ResourceProvider { path ->
                File(path).takeIf { it.isFile }?.readBytes() ?: throw FileNotFound(path)
            }
            fun converter(): Converter = Config(resources, segmentationFactories = mapOf("jieba" to JiebaSegmentationFactory()))
                .newFromFile(directory.resolve("config.json").path)
            when (row[3]) {
                "ok" -> converters[name] = converter()
                "error" -> assertFailsWith<OpenCCException>(name) { converter() }
                "abort" -> assertFailsWith<OpenCCException>(name) { converter() }
                else -> error("Unknown native status: $row")
            }
        }
        for (row in rows.filter { it[0] == "S" }) {
            val actual = models.getValue(row[1]).segmentUtf8(unhex(row[2]))
            val expected = if (row[3] == "0") emptyList() else row[4].split(',')
            assertEquals(expected, actual.map(::hex), "${row[1]} input=${row[2]}")
        }
        for (row in rows.filter { it[0] == "C" }) {
            val converter = converters.getValue(row[1])
            if (row[3] == "OK") assertEquals(row[4], hex(converter.convertUtf8(unhex(row[2]))), "${row[1]} input=${row[2]}")
            else assertFailsWith<OpenCCException>("${row[1]} input=${row[2]}") { converter.convertUtf8(unhex(row[2])) }
        }
    }
}
