/* Adapted from OpenCC. Copyright 2010-2026 Carbo Kuo and contributors. Apache-2.0. */
package io.github.opencckmp

data class ConfigLoadOptions(val includeTofuRiskDictionaries: Boolean = true)
data class ConfigWarning(val path: String, val message: String, val schemaPath: String = "", val keyword: String = "")

fun interface SegmentationFactory {
    fun create(properties: SegmentationProperties, resources: ResourceProvider): Segmentation
    /** Resolved provider names needed before synchronous initialization; hosts may apply option logic. */
    fun resourcesToPrepare(properties: SegmentationProperties, declaredResources: List<String>): List<String> = declaredResources
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
        return runSynchronous { load(json, configDirectory, options, { provider.read(it) }, { _, _, _ -> provider }) }
    }

    internal suspend fun prepareFromString(json: String, configDirectory: String, options: ConfigLoadOptions,
                                          read: suspend (String) -> ByteArray): Converter =
        load(json, configDirectory, options, read) { factory, properties, declared ->
            val resources = linkedMapOf<String, ByteArray>()
            for (name in factory.resourcesToPrepare(properties, declared).distinct()) resources[name] = read(name)
            MemoryResourceProvider(resources)
        }

    private suspend fun load(json: String, configDirectory: String, options: ConfigLoadOptions,
                             read: suspend (String) -> ByteArray,
                             pluginResources: suspend (SegmentationFactory, SegmentationProperties, List<String>) -> ResourceProvider): Converter {
        val root = parseConfiguration(json)
        ConfigSchema.warning(root)?.let(warningSink)
        val loaded = mutableMapOf<String, Dict>()
        suspend fun parseDict(doc: JsonValue.Object, includeTofu: Boolean): Dict? {
            val type = doc.string("type")
            if (type == "inline") {
                if (doc["may_output_tofu"] != null) throw InvalidFormat("Inline dictionary does not support may_output_tofu")
                val entries = doc.obj("entries").members.map { (key, value) ->
                    if (key.isEmpty()) throw InvalidFormat("Inline dictionary key must be a non-empty string")
                    val text = (value as? JsonValue.StringValue)?.value
                    if (text.isNullOrEmpty()) throw InvalidFormat("Inline dictionary value must be a non-empty string: ${key.substringBefore('\u0000')}")
                    // RapidJSON validates explicit JSON lengths, then upstream creates C-string
                    // dictionary entries. Preserve its observable prefix-at-NUL config semantics.
                    DictEntry(key.substringBefore('\u0000'), listOf(text.substringBefore('\u0000')))
                }
                val lexicon = Lexicon(entries).sorted()
                for (index in 1 until lexicon.size) if (lexicon[index - 1].key == lexicon[index].key) {
                    throw InvalidFormat("Inline dictionary contains duplicate key: ${lexicon[index].key}")
                }
                return TextDict(lexicon)
            }
            val tofu = when (val value = doc["may_output_tofu"]) {
                null -> false
                is JsonValue.BooleanValue -> value.value
                else -> throw InvalidFormat("Property must be a boolean: may_output_tofu")
            }
            if (tofu && !includeTofu) return null
            if (type == "group") {
                val policy = when (val value = doc["match_policy"]) {
                    null -> DictGroupMatchPolicy.ShortCircuit
                    is JsonValue.StringValue -> when (val policy = value.value.substringBefore('\u0000')) {
                        "short_circuit" -> DictGroupMatchPolicy.ShortCircuit
                        "union" -> DictGroupMatchPolicy.Union
                        else -> throw InvalidFormat("Unknown dictionary group match_policy: $policy")
                    }
                    else -> throw InvalidFormat("Property must be a string: match_policy")
                }
                val dicts = doc.array("dicts").values.mapNotNull { value ->
                    parseDict(value as? JsonValue.Object ?: throw InvalidFormat("Element of the array must be an object"), includeTofu)
                }
                return if (dicts.isEmpty()) null else DictGroup(dicts, policy)
            }
            val file = resolveResource(configDirectory, doc.string("file"))
            val reader = readers[type] ?: throw InvalidFormat("Unknown dictionary type: $type")
            return loaded.getOrPut(type + "\n" + file) {
                val bytes = read(file)
                dictionaryCache?.load(cacheIdentity, file, bytes, reader) ?: reader.read(bytes)
            }
        }
        suspend fun chain(value: JsonValue.Array, includeTofu: Boolean): ConversionChain = ConversionChain(
            value.values.mapNotNull { item ->
                // Upstream ignores non-object conversion steps after advisory schema validation.
                val doc = item as? JsonValue.Object ?: return@mapNotNull null
                parseDict(doc.obj("dict"), includeTofu)?.let { Conversion(it) }
            })
        val segmentation = root["segmentation"]?.let { value ->
            val doc = value as? JsonValue.Object ?: throw InvalidFormat("Property must be an object: segmentation")
            val type = doc.string("type")
            if (type == "mmseg") {
                MaxMatchSegmentation(parseDict(doc.obj("dict"), true) ?: TextDict(Lexicon(emptyList())))
            } else {
                val properties = mutableListOf("__config_dir" to configDirectory)
                val declared = mutableListOf<String>()
                doc["resources"]?.let { resources ->
                    for ((name, resource) in (resources as? JsonValue.Object ?: throw InvalidFormat("Property must be an object: resources")).members) {
                        val text = (resource as? JsonValue.StringValue)?.value?.substringBefore('\u0000') ?: throw InvalidFormat("Segmentation resource must be a string: ${name.substringBefore('\u0000')}")
                        properties.add(name.substringBefore('\u0000') to text)
                        declared.add(resolveResource(configDirectory, text))
                    }
                }
                for ((name, property) in doc.members) {
                    val key = name.substringBefore('\u0000')
                    if (key != "type" && key != "resources") {
                        val text = (property as? JsonValue.StringValue)?.value?.substringBefore('\u0000') ?: throw InvalidFormat("Segmentation plugin property must be a string: $key")
                        properties.add(key to text)
                    }
                }
                val factory = segmentationFactories[type] ?: throw UnavailableSegmentationExtension(type)
                val pairs = SegmentationProperties(properties)
                factory.create(pairs, pluginResources(factory, pairs, declared))
            }
        }
        val main = SingleStageConverter(segmentation, chain(root.array("conversion_chain"), options.includeTofuRiskDictionaries))
        return root["normalization"]?.let { value ->
            val normalized = value as? JsonValue.Array ?: throw InvalidFormat("Property must be an array: normalization")
            ConfigBasedConverter(SingleStageConverter(null, chain(normalized, options.includeTofuRiskDictionaries)), main)
        } ?: main
    }

}

internal fun parseConfiguration(json: String): JsonValue.Object {
    val document = try { JsonParser(json.substringBefore('\u0000')).parse() }
        catch (_: InvalidFormat) { throw InvalidFormat("Error parsing JSON") }
    return document as? JsonValue.Object ?: throw InvalidFormat("Root of configuration must be an object")
}

internal fun JsonValue.Object.property(name: String): JsonValue = this[name] ?: throw InvalidFormat("Required property not found: $name")
internal fun JsonValue.Object.string(name: String): String =
    (property(name) as? JsonValue.StringValue)?.value?.substringBefore('\u0000') ?: throw InvalidFormat("Property must be a string: $name")
internal fun JsonValue.Object.obj(name: String): JsonValue.Object =
    property(name) as? JsonValue.Object ?: throw InvalidFormat("Property must be an object: $name")
internal fun JsonValue.Object.array(name: String): JsonValue.Array =
    property(name) as? JsonValue.Array ?: throw InvalidFormat("Property must be an array: $name")
