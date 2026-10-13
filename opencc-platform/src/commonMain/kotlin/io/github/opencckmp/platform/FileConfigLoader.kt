/* Adapted from OpenCC. Copyright 2010-2026 contributors. Apache-2.0. */
package io.github.opencckmp.platform

import io.github.opencckmp.*

/**
 * Local-file configuration loading with upstream provider precedence. Conversion remains in common Kotlin.
 * Explicit providers own dictionary lookup; otherwise dictionaries search the config directory, `.`, then
 * caller paths. Package/application locations are supplied by the host instead of depending on bundled data.
 */
class FileConfigLoader(
    private val provider: ResourceProvider? = null,
    searchPaths: List<String> = emptyList(),
    executablePath: String? = null,
    private val packageDataDirectory: String? = null,
    private val dataDirectory: String? = environmentDataDirectory(),
    readers: List<DictionaryReader> = standardDictionaryReaders,
    segmentationFactories: Map<String, SegmentationFactory> = emptyMap(),
    private val warningSink: ((ConfigWarning) -> Unit)? = null,
    private val dictionaryCache: DictionaryCache? = null,
    cacheIdentity: Any? = null,
) {
    private val paths = searchPaths.toList() + listOfNotNull(executablePath?.let(::parentDirectory)?.takeIf { it.isNotEmpty() })
    private val readers = readers.toList()
    private val factories = segmentationFactories.toMap()
    private val identity = cacheIdentity ?: provider ?: Any()

    fun newFromFile(fileName: String, options: ConfigLoadOptions = ConfigLoadOptions()): Converter {
        if (provider != null) {
            val resource = try { provider.getResource(fileName) } catch (_: FileNotFound) { null }
            if (resource != null) return parse(resource.bytes, parentDirectory(resource.name), provider, options)
        }
        val path = findConfigFile(fileName)
        if (resourceFileMetadata(path) == null) throw FileNotFound(path)
        val directory = parentDirectory(path)
        val dictionaries = provider ?: FileResourceProvider(listOfNotNull(directory.takeIf { it.isNotEmpty() }) +
            "." + paths.filter { it.isNotEmpty() } + listOfNotNull(packageDataDirectory?.takeIf { it.isNotEmpty() }))
        return parse(readFileBytes(path), directory, dictionaries, options)
    }

    /** JSON text with filesystem search paths. Explicit providers receive dictionary names as declared. */
    fun newFromString(json: String, configDirectory: String = "", options: ConfigLoadOptions = ConfigLoadOptions()): Converter {
        val directory = if (configDirectory.isEmpty()) paths.firstOrNull().orEmpty()
            else if (configDirectory.endsWith('/') || configDirectory.endsWith('\\')) configDirectory else "$configDirectory/"
        val dictionaries = provider ?: FileResourceProvider(listOfNotNull(directory.takeIf { it.isNotEmpty() }) +
            "." + paths.filter { it.isNotEmpty() } + listOfNotNull(packageDataDirectory?.takeIf { it.isNotEmpty() }))
        return loader(dictionaries).newFromString(json, directory, options, null)
    }

    private fun parse(bytes: ByteArray, directory: String, dictionaries: ResourceProvider, options: ConfigLoadOptions): Converter =
        loader(dictionaries).newFromString(UTF8Util.decode(bytes), directory, options, null)

    private fun loader(dictionaries: ResourceProvider): Config = Config(dictionaries, readers, factories, warningSink, dictionaryCache, identity)

    private fun findConfigFile(name: String): String {
        if (canReadConfigPath(name)) return name
        packageDataDirectory?.takeIf { it.isNotEmpty() }?.let { root ->
            val prefixed = if (root.endsWith('/') || root.endsWith('\\')) root + name else "$root/$name"
            if (canReadConfigPath(prefixed)) return prefixed
            if (canReadConfigPath("$prefixed.json")) return "$prefixed.json"
        }
        for (root in paths) {
            val candidate = "$root/$name"
            if (canReadConfigPath(candidate)) return candidate
        }
        dataDirectory?.let { root ->
            val candidate = "$root/$name"
            if (canReadConfigPath(candidate)) return candidate
        }
        throw FileNotFound(name)
    }

    private fun parentDirectory(path: String): String {
        var separator = path.lastIndexOf('/')
        if (separator < 0) separator = path.lastIndexOf('\\')
        return if (separator < 0) "" else path.substring(0, separator + 1)
    }
}

internal expect fun environmentDataDirectory(): String?
internal expect fun canReadConfigPath(path: String): Boolean
