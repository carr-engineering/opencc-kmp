package io.github.opencckmp

import java.io.File
import kotlin.test.*

class UpstreamEncodedTest {
    @Test fun everyRecordedNativePrefixNulAndMalformedCase() {
        val root = File(System.getProperty("opencc.projectRoot") ?: error("Run upstreamConformance"))
        val reference = JsonParser(File(root, ".cache/conformance/encoded-cases.json").readText()).parse() as JsonValue.Object
        val engines = reference.obj("configs").members.associate { (name, _) ->
            val json = File(root, ".cache/conformance/encoded/$name.json").readText()
            name to Config(ResourceProvider { File(root, ".cache/conformance/encoded/$it").readBytes() }).newFromString(json)
        }
        var count = 0
        for (item in reference.array("cases").values) {
            val row = item as JsonValue.Object
            fun bytes(name: String) = row.string(name).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val input = bytes("input_hex")
            val length = (row["length"] as JsonValue.Number).value.toInt()
            val end = if (length < 0) input.size else length
            val operation = row.string("operation")
            val mode = if (operation in listOf("cstring", "buffer") || length < 0) EncodedInputMode.NullTerminated else EncodedInputMode.LengthDelimited
            val engine = engines.getValue(row.string("config"))
            val label = "${row.string("config")} / $operation / $length / ${row.string("input_hex")}"
            fun converted(): ByteArray {
                if (operation == "inspect") {
                    val actual = engine.inspectUtf8(input, 0, end)
                    (row["inspection"] as? JsonValue.Object)?.let { compareInspection(it, actual, label) }
                    return actual.output
                }
                if (operation.startsWith("stream")) {
                    val stream = Utf8ConverterStream(engine, operation.removePrefix("stream").toInt())
                    val chunks = input.asList().chunked(length).map { stream.convertChunk(it.toByteArray()) } + listOf(stream.finish())
                    val expected = row["chunks"] as? JsonValue.Array
                    if (expected != null) {
                        assertEquals(expected.values.size, chunks.size, label)
                        expected.values.zip(chunks).forEach { (hex, bytes) -> assertContentEquals(decode((hex as JsonValue.StringValue).value), bytes, label) }
                    }
                    return joinEncoded(chunks)
                }
                if (operation != "buffer") return engine.convertUtf8(input, 0, end, mode)
                val output = ByteArray(input.size * 32 + 4096)
                val written = engine.convertUtf8To(input, output, 0, 0, end, mode)
                return output.copyOf(written)
            }
            if (row.string("status") == "ERROR") assertFailsWith<InvalidUTF8>(label) { converted() }
            else {
                val expected = bytes("output_hex")
                assertContentEquals(expected, converted(), label)
                val text = runCatching { UTF8Util.decode(input, 0, end) }.getOrNull()
                if (text != null && operation == "view") {
                    assertContentEquals(expected, UTF8Util.encode(engine.convert(text)), "$label / Kotlin String equivalent")
                    assertEquals(engine.convert(text), engine.convertWithMap(text).output, "$label / composed UTF-16 map")
                } else if (text != null && operation == "inspect") {
                    compareInspection(row.obj("inspection"), engine.inspect(text).toEncoded(), "$label / typed text inspection")
                }
            }
            count++
        }
        assertTrue(count >= 5832)
        println("Native encoded-input operations compared: $count")
    }
    private fun decode(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun compareInspection(expected: JsonValue.Object, actual: EncodedInspectionResult, label: String) {
        assertContentEquals(decode(expected.string("input")), actual.input, label)
        assertContentEquals(decode(expected.string("output")), actual.output, label)
        fun segments(expected: JsonValue.Array, actual: List<ByteArray>) {
            assertEquals(expected.values.size, actual.size, label)
            expected.values.zip(actual).forEach { (hex, bytes) -> assertContentEquals(decode((hex as JsonValue.StringValue).value), bytes, label) }
        }
        segments(expected.array("segments"), actual.segments)
        assertEquals(expected.array("stages").values.size, actual.stages.size, label)
        expected.array("stages").values.zip(actual.stages).forEach { (value, stage) ->
            val doc = value as JsonValue.Object
            assertEquals((doc["index"] as JsonValue.Number).value.toInt(), stage.index, label)
            segments(doc.array("segments"), stage.segments)
        }
        assertEquals(expected.array("pipelineStages").values.size, actual.pipelineStages.size, label)
        expected.array("pipelineStages").values.zip(actual.pipelineStages).forEach { (value, child) -> compareInspection(value as JsonValue.Object, child, label) }
    }
}
