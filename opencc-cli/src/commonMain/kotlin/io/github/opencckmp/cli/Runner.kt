@file:OptIn(io.github.opencckmp.ExperimentalOpenCCApi::class)
package io.github.opencckmp.cli

import io.github.opencckmp.*
import io.github.opencckmp.jieba.JiebaSegmentationFactory
import io.github.opencckmp.phrase.PhraseExtract
import io.github.opencckmp.tools.*
import io.github.opencckmp.zip.ZipResourceProvider
import kotlin.time.TimeSource
import kotlin.time.DurationUnit

/** Run one isolated tool invocation. All mutable state, streams and errors belong to this call. */
fun runCli(tool: CliTool, arguments: List<String>, host: CliHost, program: String = tool.program): Int = try {
    val parsed = parse(tool, arguments)
    when (tool) {
        CliTool.Convert -> ConvertInvocation(parsed, host, program).run()
        CliTool.Dictionary -> {
            val inputs = parsed.all("input").map { host.openInput(it).use(::readAll) }
            val output = try { DictionaryConverter.convert(inputs, DictionaryFormat.fromName(parsed["from"]!!), DictionaryFormat.fromName(parsed["to"]!!)) }
            catch (error: InvalidTextDictionary) {
                if (!error.message.orEmpty().contains("Tabular not found")) throw error
                val bytes = inputs.first()
                var start = 0
                repeat(error.lineNumber - 1) { while (start < bytes.size && bytes[start++] != 10.toByte()) { } }
                var end = start
                while (end < bytes.size && bytes[end] != 10.toByte() && bytes[end] != 0.toByte()) end++
                if (end < bytes.size && bytes[end] == 10.toByte()) end++
                throw RawCliFailure("Invalid text dictionary at line ${error.lineNumber}: Tabular not found ".encodeToByteArray() + bytes.copyOfRange(start, end))
            }
            host.openOutput(parsed["output"]!!).use { it.write(output) }
        }
        CliTool.Phrase -> {
            // The pinned extractor silently ignores failed ifstreams/ofstreams and returns success.
            val bytes = parsed.files.map { path -> runCatching { host.openInput(path).use(::readAll) }.getOrElse { byteArrayOf() } }
            val extractor = PhraseExtract()
            extractor.extract(UTF8Util.decode(bytes.fold(byteArrayOf()) { all, next -> all + next }))
            runCatching { host.openOutput(parsed["output"]!!).use { output ->
                for (word in extractor.words) {
                    val signal = extractor.signal(word)
                    val line = listOf(word, signal.frequency.toString(), host.formatNumber(extractor.logProbability(word)),
                        host.formatNumber(signal.cohesion), host.formatNumber(signal.prefixEntropy + signal.suffixEntropy),
                        host.formatNumber(signal.prefixEntropy), host.formatNumber(signal.suffixEntropy)).joinToString(" ") + "\n"
                    output.write(UTF8Util.encode(line)); output.flush()
                }
            } }
        }
    }
    0
} catch (display: DisplayRequested) {
    host.output.write(display(tool, program, display.version).encodeToByteArray()); 0
} catch (error: ParseError) {
    host.message(parseFailure(tool, program, error)); 1
} catch (error: RawCliFailure) {
    host.error.write(error.bytes + 10.toByte()); host.error.flush(); 1
} catch (error: OpenCCException) {
    val context = (error as? InvalidUTF8)?.encodedContext
    if (context != null) { host.error.write("Invalid UTF8: ".encodeToByteArray() + context + 10.toByte()); host.error.flush() }
    else host.message((if (error is InvalidTextDictionary) error.message.orEmpty().removePrefix("Invalid format: ") else error.message.orEmpty()) + "\n")
    if (tool == CliTool.Phrase) 0 else 1
} catch (error: CliFailure) {
    host.message(error.message.orEmpty() + "\n"); if (tool == CliTool.Phrase) 0 else 1
}
private class RawCliFailure(val bytes: ByteArray) : Exception()

private class ConvertInvocation(val args: ParsedArguments, val host: CliHost, val program: String) {
    private val started = TimeSource.Monotonic.markNow()
    private var loadMs = 0.0
    private var convertMs = 0.0
    private var writeMs = 0.0
    private var inputBytes = 0L
    private var outputBytes = 0L
    private var warned = false
    private var scanTail = byteArrayOf()
    private val mode = when { args.flag("segmentation") -> "segmentation"; args.flag("inspect") -> "inspect"; args.flag("ambiguities") -> "ambiguities"; else -> "convert" }
    private val noFlush = args.flag("noflush") || args["output"] != null
    private lateinit var converter: Converter
    fun run() {
        if (listOf("segmentation", "inspect", "ambiguities").count { args.flag(it) } > 1)
            throw CliFailure("error: --segmentation, --inspect and --ambiguities are mutually exclusive.")
        val loading = TimeSource.Monotonic.markNow()
        converter = loadConfig(args, host, program)
        loadMs = loading.elapsedNow().toDouble(DurationUnit.MILLISECONDS)
        if (mode == "segmentation" && converter.segmentation == null)
            throw CliFailure("error: this configuration has no segmentation step; --segmentation is not supported.")
        if (mode == "ambiguities" && converter.conversionChain == null)
            throw CliFailure("error: this configuration has no single conversion chain; --ambiguities is not supported.")
        val name = args["input"]
        val input = if (name == null) host.input else host.openInput(name)
        var temporary: String? = null
        val target = args["output"]
        var actualTarget = target
        try {
            if (name != null && target != null && host.sameFile(name, target)) {
                if (!args.flag("in-place")) throw CliFailure("Input and output refer to the same file; use --in-place to overwrite it.")
                actualTarget = host.realPath(target)
                if (host.multipleLinks(actualTarget)) throw CliFailure("--in-place cannot safely update files with multiple hard links.")
                temporary = host.temporaryNear(actualTarget)
            }
            val output = if (target == null) host.output else host.openOutput(temporary ?: target)
            try {
                if (name == null && host.interactive) host.message(if (host.windows) "Reading from standard input. Press Ctrl+Z then Enter to finish.\n" else "Reading from standard input. Press Ctrl+D to finish.\n")
                if (mode == "inspect" || mode == "segmentation") lines(input, output, name != null)
                else if (mode == "ambiguities") ambiguities(input, output)
                else convert(input, output)
            } finally { if (target != null) output.close() else output.flush() }
            if (name != null) input.close()
            if (temporary != null) { host.preserveMetadata(actualTarget!!, temporary); host.replace(temporary, actualTarget); temporary = null }
        } finally { if (name != null) input.close(); temporary?.let { host.remove(it) } }
        args["measured_result"]?.let { path ->
            val json = JsonOutput().raw("{\n    \"config\": ").string(args["config"]!!).raw(",\n    \"input\": ")
            name?.let { json.string(it) } ?: json.raw("null")
            json.raw(",\n    \"output\": "); target?.let { json.string(it) } ?: json.raw("null")
            json.raw(",\n    \"mode\": ").string(if (name == null) "line_by_line" else "file")
                .raw(",\n    \"output_mode\": ").string(mode)
                .raw(",\n    \"load_ms\": $loadMs,\n    \"convert_ms\": $convertMs,\n    \"write_ms\": $writeMs,\n    \"total_ms\": ${started.elapsedNow().toDouble(DurationUnit.MILLISECONDS)},\n    \"input_bytes\": $inputBytes,\n    \"output_bytes\": $outputBytes\n}\n")
            host.openOutput(path).use { it.write(json.toBytes()) }
        }
    }
    private fun write(output: CliOutput, bytes: ByteArray, measured: Int = bytes.size) {
        outputBytes += measured
        val time = TimeSource.Monotonic.markNow()
        val nul = bytes.indexOf(0)
        output.write(if (nul < 0) bytes else bytes.copyOf(nul))
        if (!noFlush) output.flush()
        writeMs += time.elapsedNow().toDouble(DurationUnit.MILLISECONDS)
    }
    private fun convert(input: CliInput, output: CliOutput) {
        val stream = Utf8ConverterStream(converter)
        while (true) {
            val block = readBlock(input)
            inputBytes += block.bytes.size
            warn(block.bytes, true)
            val time = TimeSource.Monotonic.markNow()
            val converted = if (block.final) stream.finish(block.bytes) else stream.convertChunk(block.bytes)
            convertMs += time.elapsedNow().toDouble(DurationUnit.MILLISECONDS)
            write(output, converted)
            if (block.final) return
        }
    }
    private fun lines(input: CliInput, output: CliOutput, file: Boolean) {
        val reader = Lines(input)
        var first = true
        while (true) {
            var line = reader.next() ?: break
            if (file && line.lastOrNull() == 13.toByte()) line = line.copyOf(line.size - 1)
            if (!first) output.write(byteArrayOf(10)) else first = false
            inputBytes += line.size
            warn(line, false)
            val time = TimeSource.Monotonic.markNow()
            val bytes = if (mode == "inspect") JsonOutput().inspection(converter.inspectUtf8(line)).toBytes() else {
                val segments = converter.segmentation!!.segmentUtf8(line).map { segment -> val nul = segment.indexOf(0); if (nul < 0) segment else segment.copyOf(nul) }
                JsonOutput().raw("{\"input\":").string(line).raw(",\"segments\":").segments(segments).raw("}").toBytes()
            }
            convertMs += time.elapsedNow().toDouble(DurationUnit.MILLISECONDS)
            write(output, bytes)
        }
    }
    private fun ambiguities(input: CliInput, output: CliOutput) {
        val stream = Utf8AmbiguityStream(converter)
        var converted = 0L
        var count = 0L
        while (true) {
            val block = readBlock(input)
            inputBytes += block.bytes.size
            warn(block.bytes, true)
            val time = TimeSource.Monotonic.markNow()
            val chunk = if (block.final) stream.finish(block.bytes) else stream.convertChunk(block.bytes)
            convertMs += time.elapsedNow().toDouble(DurationUnit.MILLISECONDS)
            converted += chunk.output.size; count += chunk.ambiguities.size
            val writing = TimeSource.Monotonic.markNow()
            fun record(output: CliOutput, bytes: ByteArray) { outputBytes += bytes.size; output.write(bytes) }
            for (source in chunk.newSources) record(output, JsonOutput().raw("{\"def\":").string(validated(source)).raw("}\n").toBytes())
            var consumed = 0
            fun literal(until: Int) { if (until > consumed) record(output, JsonOutput().raw("{\"lit\":").string(validated(chunk.output.copyOfRange(consumed, until))).raw("}\n").toBytes()) }
            for (span in chunk.ambiguities) {
                literal(span.outputOffset)
                record(output, JsonOutput().raw("{\"amb\":{\"t\":").string(validated(chunk.output.copyOfRange(span.outputOffset, span.outputOffset + span.outputLength))).raw(",\"s\":${span.sourceIndex}}}\n").toBytes())
                consumed = span.outputOffset + span.outputLength
            }
            literal(chunk.output.size)
            if (!noFlush) output.flush()
            writeMs += writing.elapsedNow().toDouble(DurationUnit.MILLISECONDS)
            if (block.final) break
        }
        write(output, JsonOutput().raw("{\"end\":{\"output_bytes\":$converted,\"ambiguities\":$count,\"sources\":${stream.sourceCount}}}\n").toBytes())
    }
    private fun validated(bytes: ByteArray): ByteArray {
        try { UTF8Util.decode(bytes) } catch (_: InvalidUTF8) { throw CliFailure(invalidAmbiguityEncoding) }
        return bytes
    }
    private fun warn(bytes: ByteArray, streaming: Boolean) {
        if (warned) return
        val input = if (streaming) scanTail + bytes else bytes
        var position = 0
        while (position < input.size) {
            val first = input[position].toInt() and 255
            val width = when { first < 128 -> 1; first in 0xc0..0xdf -> 2; first in 0xe0..0xef -> 3; first in 0xf0..0xf7 -> 4; first in 0xf8..0xfb -> 5; first in 0xfc..0xfd -> 6; else -> 0 }
            if (width == 0) { position++; continue }
            if (width > input.size - position) break
            var point = first and ((1 shl (7 - width)) - 1)
            if (width == 1) point = first
            for (byte in 1 until width) point = (point shl 6) or (input[position + byte].toInt() and 63)
            if (point in 0xfe00..0xfe0f || point in 0xe0100..0xe01ef) {
                warned = true; host.message("warning: input contains Unicode variation selectors (possible IVS); conversion results may be inaccurate.\n"); return
            }
            position += width
        }
        if (streaming) scanTail = input.copyOfRange(maxOf(0, input.size - 4), input.size)
    }
}
private const val invalidAmbiguityEncoding = "Converted output contains invalid UTF-8 (from the input or a dictionary); --ambiguities emits machine-readable JSON and cannot represent it. Aborting record stream."
private class Block(val bytes: ByteArray, val final: Boolean)
private fun readBlock(input: CliInput): Block {
    val bytes = ByteArray(1024 * 1024)
    var count = 0
    while (count < bytes.size) {
        val read = input.read(bytes, count, bytes.size - count)
        if (read == 0) return Block(bytes.copyOf(count), true)
        count += read
    }
    return Block(bytes, false)
}
private class Lines(val input: CliInput) {
    private val buffer = ByteArray(65536)
    private var offset = 0
    private var size = 0
    fun next(): ByteArray? {
        var line = ByteArray(256)
        var length = 0
        while (true) {
            if (offset == size) { size = input.read(buffer); offset = 0; if (size == 0) return if (length == 0) null else line.copyOf(length) }
            val byte = buffer[offset++]
            if (byte == 10.toByte()) return line.copyOf(length)
            if (length == line.size) {
                if (length == Int.MAX_VALUE) throw CliFailure("Input line exceeds Kotlin array capacity")
                line = line.copyOf(if (line.size > Int.MAX_VALUE / 2) Int.MAX_VALUE else line.size * 2)
            }
            line[length++] = byte
        }
    }
}
