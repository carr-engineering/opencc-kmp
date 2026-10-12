package io.github.opencckmp.cli

import io.github.opencckmp.*

/** Byte-based JSON string output preserves the pinned human-inspection encoding policy. */
internal class JsonOutput {
    private var bytes = ByteArray(256)
    private var size = 0
    fun raw(text: String): JsonOutput { append(text.encodeToByteArray()); return this }
    fun string(text: String): JsonOutput = string(UTF8Util.encode(text))
    fun string(value: ByteArray): JsonOutput {
        raw("\"")
        for (byte in value) when (val code = byte.toInt() and 255) {
            34 -> raw("\\\""); 92 -> raw("\\\\"); 8 -> raw("\\b"); 9 -> raw("\\t")
            10 -> raw("\\n"); 12 -> raw("\\f"); 13 -> raw("\\r")
            in 0..31 -> raw("\\u00" + "0123456789ABCDEF"[code ushr 4] + "0123456789ABCDEF"[code and 15])
            else -> put(byte)
        }
        raw("\""); return this
    }
    fun segments(values: List<ByteArray>): JsonOutput {
        raw("[")
        values.forEachIndexed { index, value -> if (index > 0) raw(","); string(value) }
        return raw("]")
    }
    fun inspection(result: EncodedInspectionResult): JsonOutput {
        raw("{\"input\":"); string(result.input); raw(",\"segments\":"); segments(result.segments); raw(",\"stages\":[")
        result.stages.forEachIndexed { index, stage ->
            if (index > 0) raw(",")
            raw("{\"index\":${stage.index},\"segments\":"); segments(stage.segments); raw("}")
        }
        raw("],\"pipelineStages\":[")
        result.pipelineStages.forEachIndexed { index, stage -> if (index > 0) raw(","); inspection(stage) }
        raw("],\"output\":"); string(result.output); return raw("}")
    }
    fun toBytes(): ByteArray = bytes.copyOf(size)
    private fun put(value: Byte) {
        if (size == bytes.size) {
            if (size == Int.MAX_VALUE) throw CliFailure("JSON output exceeds Kotlin array capacity")
            bytes = bytes.copyOf(minOf(Int.MAX_VALUE.toLong(), bytes.size.toLong() * 2).toInt())
        }
        bytes[size++] = value
    }
    private fun append(value: ByteArray) {
        if (value.size > Int.MAX_VALUE - size) throw CliFailure("JSON output exceeds Kotlin array capacity")
        if (size + value.size > bytes.size) bytes = bytes.copyOf(maxOf(size + value.size, minOf(Int.MAX_VALUE.toLong(), bytes.size.toLong() * 2).toInt()))
        value.copyInto(bytes, size); size += value.size
    }
}
