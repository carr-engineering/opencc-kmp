/* Adapted from OpenCC. Copyright 2010-2026 Carbo Kuo and contributors. Apache-2.0. */
package io.github.opencckmp

data class ConfigLoadOptions(val includeTofuRiskDictionaries: Boolean = true)
data class ConfigWarning(val path: String, val message: String)

fun interface SegmentationFactory {
    fun create(properties: Map<String, String>, resources: ResourceProvider): Segmentation
}

/** Stateless loader. Optional caller-owned [dictionaryCache] shares dictionaries across loads. */
class Config(
    private val provider: ResourceProvider = ResourceProvider { throw FileNotFound(it) },
    readers: List<DictionaryReader> = standardDictionaryReaders,
    segmentationFactories: Map<String, SegmentationFactory> = emptyMap(),
    private val warningSink: (ConfigWarning) -> Unit = {},
    private val dictionaryCache: DictionaryCache? = null,
    private val cacheIdentity: Any = provider,
) {
    private val readers = readers.associateBy { it.type }
    private val segmentationFactories = segmentationFactories.toMap()

    fun newFromFile(fileName: String, options: ConfigLoadOptions = ConfigLoadOptions()): Converter {
        val name = fileName.replace('\\', '/')
        return newFromString(UTF8Util.decode(provider.read(name)), name.substringBeforeLast('/', ""), options)
    }

    fun newFromString(json: String, configDirectory: String = "", options: ConfigLoadOptions = ConfigLoadOptions()): Converter {
        val root = JsonParser(json).parse() as? JsonValue.Object ?: throw InvalidFormat("Root of configuration must be an object")
        schemaWarnings(root)
        val loaded = mutableMapOf<String, Dict>()
        fun parseDict(doc: JsonValue.Object, includeTofu: Boolean): Dict? {
            val type = doc.string("type")
            if (type == "inline") {
                if (doc["may_output_tofu"] != null) throw InvalidFormat("Inline dictionary does not support may_output_tofu")
                val entries = doc.obj("entries").members.map { (key, value) ->
                    if (key.isEmpty()) throw InvalidFormat("Inline dictionary key must be a non-empty string")
                    val text = (value as? JsonValue.StringValue)?.value
                    if (text.isNullOrEmpty()) throw InvalidFormat("Inline dictionary value must be a non-empty string: $key")
                    // RapidJSON validates explicit JSON lengths, then upstream creates C-string
                    // dictionary entries. Preserve its observable prefix-at-NUL config semantics.
                    DictEntry(key.substringBefore('\u0000'), listOf(text.substringBefore('\u0000')))
                }
                return TextDict(Lexicon(entries).sorted())
            }
            val tofu = when (val value = doc["may_output_tofu"]) {
                null -> false
                is JsonValue.BooleanValue -> value.value
                else -> throw InvalidFormat("Property 'may_output_tofu' must be a boolean")
            }
            if (tofu && !includeTofu) return null
            if (type == "group") {
                val policy = when (val value = doc["match_policy"]) {
                    null -> DictGroupMatchPolicy.ShortCircuit
                    is JsonValue.StringValue -> when (value.value) {
                        "short_circuit" -> DictGroupMatchPolicy.ShortCircuit
                        "union" -> DictGroupMatchPolicy.Union
                        else -> throw InvalidFormat("Unknown dictionary group match_policy: ${value.value}")
                    }
                    else -> throw InvalidFormat("Property 'match_policy' must be a string")
                }
                val dicts = doc.array("dicts").values.mapNotNull { value ->
                    parseDict(value as? JsonValue.Object ?: throw InvalidFormat("Element of the array must be an object"), includeTofu)
                }
                return if (dicts.isEmpty()) null else DictGroup(dicts, policy)
            }
            val reader = readers[type] ?: throw InvalidFormat("Unknown or unavailable dictionary type: $type")
            val file = resolveResource(configDirectory, doc.string("file"))
            return loaded.getOrPut(type + "\n" + file) {
                val bytes = provider.read(file)
                dictionaryCache?.load(cacheIdentity, file, bytes, reader) ?: reader.read(bytes)
            }
        }
        fun chain(value: JsonValue.Array, includeTofu: Boolean): ConversionChain = ConversionChain(
            value.values.mapNotNull { item ->
                // Upstream ignores non-object conversion steps after advisory schema validation.
                val doc = item as? JsonValue.Object ?: return@mapNotNull null
                parseDict(doc.obj("dict"), includeTofu)?.let { Conversion(it) }
            })
        val segmentation = root["segmentation"]?.let { value ->
            val doc = value as? JsonValue.Object ?: throw InvalidFormat("Property 'segmentation' must be an object")
            val type = doc.string("type")
            if (type == "mmseg") {
                MaxMatchSegmentation(parseDict(doc.obj("dict"), true) ?: TextDict(Lexicon(emptyList())))
            } else {
                val factory = segmentationFactories[type] ?: throw InvalidFormat("Unavailable segmentation extension: $type")
                val properties = mutableMapOf("__config_dir" to configDirectory)
                doc["resources"]?.let { resources ->
                    for ((name, resource) in (resources as? JsonValue.Object ?: throw InvalidFormat("Segmentation resources must be an object")).members) {
                        properties[name] = (resource as? JsonValue.StringValue)?.value ?: throw InvalidFormat("Segmentation resource must be a string: $name")
                    }
                }
                for ((name, property) in doc.members) {
                    if (name != "type" && name != "resources") {
                        properties[name] = (property as? JsonValue.StringValue)?.value ?: throw InvalidFormat("Segmentation plugin property must be a string: $name")
                    }
                }
                factory.create(properties.toMap(), provider)
            }
        }
        val main = SingleStageConverter(segmentation, chain(root.array("conversion_chain"), options.includeTofuRiskDictionaries))
        return root["normalization"]?.let { value ->
            val normalized = value as? JsonValue.Array ?: throw InvalidFormat("Property 'normalization' must be an array")
            ConfigBasedConverter(SingleStageConverter(null, chain(normalized, options.includeTofuRiskDictionaries)), main)
        } ?: main
    }

    private fun schemaWarnings(root: JsonValue.Object) {
        // Schema diagnostics are advisory; parsing below remains the behavioural authority.
        if (root["name"] !is JsonValue.StringValue) warningSink(ConfigWarning("/name", "Expected configuration name"))
        for ((name, _) in root.members) if (name !in setOf("name", "normalization", "segmentation", "conversion_chain")) {
            warningSink(ConfigWarning("/$name", "Unknown configuration property"))
        }
        for (name in listOf("normalization", "conversion_chain")) {
            val value = root[name]
            if (value is JsonValue.Array && value.values.isEmpty()) warningSink(ConfigWarning("/$name", "Empty conversion chain"))
        }
    }
}

internal fun JsonValue.Object.string(name: String): String =
    (this[name] as? JsonValue.StringValue)?.value ?: throw InvalidFormat("Property '$name' must be a string")
internal fun JsonValue.Object.obj(name: String): JsonValue.Object =
    this[name] as? JsonValue.Object ?: throw InvalidFormat("Property '$name' must be an object")
internal fun JsonValue.Object.array(name: String): JsonValue.Array =
    this[name] as? JsonValue.Array ?: throw InvalidFormat("Property '$name' must be an array")
