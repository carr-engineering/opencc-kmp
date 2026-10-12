/* Adapted from OpenCC. Copyright 2010-2026 contributors. Apache-2.0. */
package io.github.opencckmp.jieba

import io.github.opencckmp.*

/** Register under the pinned extension name: Config(..., segmentationFactories = mapOf("jieba" to this)). */
class JiebaSegmentationFactory : SegmentationFactory {
    override fun create(properties: SegmentationProperties, resources: ResourceProvider): Segmentation {
        val paths = Paths(properties)
        val found = linkedMapOf<String, ByteArray>()
        fun find(names: List<String>): String? {
            for (name in names) try { found[name] = resources.read(name); return name } catch (_: FileNotFound) { }
            return null
        }
        var dict = find(paths.dictionary())
        if (dict == null && paths.canFallback) dict = find(paths.textFallback())
        val model = find(paths.model())
        var user = paths.user?.takeIf { it.isNotEmpty() }?.let { find(paths.candidates(it)) ?: paths.missing() }
        if (dict != null && !dict.endsWith(".ocd2") && user == null) user = find(paths.userFallback()) ?: paths.missing()
        if (dict == null || model == null) paths.missing()
        find(paths.auxiliary(dict, model, "idf.utf8")) ?: paths.missing()
        find(paths.auxiliary(dict, model, "stop_words.utf8")) ?: paths.missing()
        val userBytes = user?.let { listOf(found.getValue(it)) } ?: emptyList()
        val hmm = found.getValue(model)
        val algorithm = if (dict.endsWith(".ocd2")) JiebaSegmentation.fromBytes(MarisaDict.fromBytes(found.getValue(dict)), hmm, userBytes)
            else JiebaSegmentation.fromBytes(found.getValue(dict), hmm, userBytes)
        return PluginSegmentation(algorithm)
    }

    override suspend fun prepareResources(properties: SegmentationProperties, resources: AsyncResourceProvider,
                                          declaredResources: List<String>): ResourceProvider {
        val paths = Paths(properties)
        val found = linkedMapOf<String, ByteArray>()
        suspend fun find(names: List<String>): String? {
            for (name in names) try { found[name] = resources.read(name); return name } catch (_: FileNotFound) { }
            return null
        }
        var dict = find(paths.dictionary())
        if (dict == null && paths.canFallback) dict = find(paths.textFallback())
        val model = find(paths.model())
        if (paths.user?.isNotEmpty() == true) find(paths.candidates(paths.user)) ?: paths.missing()
        else if (dict != null && !dict.endsWith(".ocd2")) find(paths.userFallback()) ?: paths.missing()
        if (dict == null || model == null) paths.missing()
        find(paths.auxiliary(dict, model, "idf.utf8")) ?: paths.missing()
        find(paths.auxiliary(dict, model, "stop_words.utf8")) ?: paths.missing()
        return MemoryResourceProvider(found)
    }

    private class Paths(properties: SegmentationProperties) {
        private val directory = properties["__config_dir"].orEmpty().replace('\\', '/')
        private val dict = properties["dict_path"]?.takeIf { it.isNotEmpty() }
            ?: throw OpenCCException("Required resource missing: dict_path")
        private val hmm = properties["model_path"]?.takeIf { it.isNotEmpty() }
            ?: throw OpenCCException("Required resource missing: model_path")
        val user = properties["user_dict_path"]
        val canFallback = dict.endsWith("jieba_merged.ocd2")
        fun dictionary(): List<String> = candidates(dict)
        fun model(): List<String> = candidates(hmm)
        fun textFallback(): List<String> = candidates(dict.substringBeforeLast('/', "").let { join(it, "jieba.dict.utf8") })
        fun userFallback(): List<String> = candidates(dict.substringBeforeLast('/', "").let { join(it, "user.dict.utf8") })
        fun candidates(raw: String): List<String> {
            val path = raw.replace('\\', '/')
            if (path.startsWith('/') || path.length > 1 && path[1] == ':') return listOf(path)
            val parent = directory.trimEnd('/').substringBeforeLast('/', "")
            val grandparent = parent.substringBeforeLast('/', "")
            return listOf(path, join(directory, path), join(parent, path),
                join(grandparent, "deps/cppjieba/dict/" + path.substringAfterLast('/'))).distinct()
        }
        fun auxiliary(dict: String, model: String, name: String): List<String> {
            val primary = listOf(join(dict.substringBeforeLast('/', ""), name), join(model.substringBeforeLast('/', ""), name))
            val alternate = dict.replace("share/opencc/jieba_dict/", "plugins/jieba/deps/cppjieba/dict/")
            return (primary + join(alternate.substringBeforeLast('/', ""), name) + candidates("jieba_dict/$name")).distinct()
        }
        fun missing(): Nothing = throw OpenCCException("Failed to locate Jieba resource files.")
        private fun join(left: String, right: String): String = if (left.isEmpty()) right else left.trimEnd('/') + "/" + right
    }

    /** The native plugin aligns tokens to its C-string input and preserves any unmatched tail. */
    private class PluginSegmentation(private val algorithm: JiebaSegmentation) : Segmentation {
        override fun segment(text: String): Segments {
            val prefix = text.substringBefore('\u0000')
            val segments = algorithm.segment(prefix)
            if (prefix.length != text.length) incomplete(UTF8Util.byteLength(prefix), UTF8Util.byteLength(text), segments.size)
            return segments
        }
        override fun segmentUtf8(input: ByteArray, startIndex: Int, endIndex: Int): List<ByteArray> {
            require(startIndex in 0..endIndex && endIndex <= input.size)
            var end = startIndex
            while (end < endIndex && input[end] != 0.toByte()) end++
            val words = algorithm.segmentUtf8(input, startIndex, end)
            val result = if (words.isEmpty() && end > startIndex) listOf(input.copyOfRange(startIndex, end)) else words
            // The native plugin returns codepoint lengths; the engine checks that these lengths
            // consume complete encoded characters and cover the original (explicit-length) input.
            var cursor = startIndex
            while (cursor < end) {
                val first = input[cursor].toInt() and 255
                val width = when {
                    first < 128 -> 1
                    first in 0xc0..0xdf -> 2
                    first in 0xe0..0xef -> 3
                    first in 0xf0..0xf7 -> 4
                    first in 0xf8..0xfb -> 5
                    first in 0xfc..0xfd -> 6
                    else -> throw InvalidUTF8("Invalid Jieba encoded input")
                }
                if (cursor + width > end) throw OpenCCException("Input text is not valid UTF-8.")
                cursor += width
            }
            if (end != endIndex) incomplete(end - startIndex, endIndex - startIndex, result.size)
            return result
        }
        private fun incomplete(consumed: Int, total: Int, segments: Int): Nothing = throw OpenCCException(
            "Segmentation plugin lengths do not cover the full input. Consumed bytes: $consumed, total bytes: $total, segments: $segments.")
    }
}
