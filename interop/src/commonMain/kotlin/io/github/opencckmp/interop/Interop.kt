@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package io.github.opencckmp.interop

import io.github.opencckmp.Config
import io.github.opencckmp.ConfigLoadOptions
import io.github.opencckmp.Converter
import io.github.opencckmp.ConverterStream
import io.github.opencckmp.ConversionInspectionResult
import io.github.opencckmp.ResourceProvider
import io.github.opencckmp.UTF8Util
import io.github.opencckmp.convertWithMap
import kotlin.js.JsExport

/** Host-owned binary resources. JS uses Int8Array; Swift uses KotlinByteArray. Input is copied. */
@JsExport
class OpenCCResources {
    private val bytes = mutableMapOf<String, ByteArray>()

    fun put(name: String, data: ByteArray) { bytes[name] = data.copyOf() }
    fun putText(name: String, text: String) { put(name, UTF8Util.encode(text)) }
    fun remove(name: String): Boolean = bytes.remove(name) != null
    fun clear() { bytes.clear() }

    /** Load synchronously after the host has prepared resources. No networking or data is bundled. */
    @Throws(Exception::class)
    fun load(configName: String, includeTofuRiskDictionaries: Boolean): OpenCCSession {
        val literal = configName.replace('\\', '/')
        val alias = literal.isNotEmpty() && literal.none { it in ".\\/:" }
        // Resolve the root before loading so dictionary/model/parse failures never trigger fallback.
        val name = if (!alias || bytes.containsKey(literal)) literal else "$literal.json"
        return OpenCCSession(loader().newFromFile(name, ConfigLoadOptions(includeTofuRiskDictionaries)))
    }

    @Throws(Exception::class)
    fun loadJson(json: String, configDirectory: String, includeTofuRiskDictionaries: Boolean): OpenCCSession =
        OpenCCSession(loader().newFromString(json, configDirectory, ConfigLoadOptions(includeTofuRiskDictionaries)))

    private fun loader(): Config = Config(ResourceProvider { name ->
        bytes[name]?.copyOf() ?: throw io.github.opencckmp.FileNotFound(name)
    })
}

/** Immutable converter behind a caller-owned closable facade. Returned strings/arrays are owned values. */
@JsExport
class OpenCCSession internal constructor(private var delegate: Converter?) {
    private fun current(): Converter = checkNotNull(delegate) { "Converter session is closed" }

    @Throws(Exception::class)
    fun convert(text: String): String = current().convert(text)
    @Throws(Exception::class)
    fun convertRange(text: String, startIndex: Int, endIndex: Int): String = current().convert(text, startIndex, endIndex)
    @Throws(Exception::class)
    fun convertPrefix(text: String, length: Int): String = current().convertPrefix(text, length)
    @Throws(Exception::class)
    fun convertUtf8(bytes: ByteArray): ByteArray = current().convertUtf8(bytes)
    @Throws(Exception::class)
    fun inspect(text: String): OpenCCInspection = current().inspect(text).exported()
    @Throws(Exception::class)
    fun convertWithSegments(text: String): OpenCCMappedResult {
        val result = current().convertWithMap(text)
        return OpenCCMappedResult(result.output, result.map.map {
            OpenCCSegment(it.sourceStart, it.sourceEnd, it.targetStart, it.targetEnd)
        }.toTypedArray())
    }
    @Throws(Exception::class)
    fun stream(maxKeepChars: Int): OpenCCStream = OpenCCStream(ConverterStream(current(), maxKeepChars))
    fun close() { delegate = null }
}

/** Independent mutable streaming state retains its converter if the originating session closes. */
@JsExport
class OpenCCStream internal constructor(private val delegate: ConverterStream) {
    @Throws(Exception::class)
    fun convertChunk(text: String): String = delegate.convertChunk(text)
    @Throws(Exception::class)
    fun finish(text: String): String = delegate.finish(text)
}

@JsExport
class OpenCCInspection internal constructor(
    val input: String,
    val output: String,
    val segments: Array<String>,
    val stages: Array<OpenCCInspectionStage>,
    val pipelineStages: Array<OpenCCInspection>,
)

@JsExport
class OpenCCInspectionStage internal constructor(val index: Int, val segments: Array<String>)
@JsExport
class OpenCCSegment internal constructor(val sourceStart: Int, val sourceEnd: Int, val targetStart: Int, val targetEnd: Int)
@JsExport
class OpenCCMappedResult internal constructor(val output: String, val segments: Array<OpenCCSegment>)

private fun ConversionInspectionResult.exported(): OpenCCInspection = OpenCCInspection(input, output,
    segments.toTypedArray(), stages.map { OpenCCInspectionStage(it.index, it.segments.toTypedArray()) }.toTypedArray(),
    pipelineStages.map { it.exported() }.toTypedArray())
