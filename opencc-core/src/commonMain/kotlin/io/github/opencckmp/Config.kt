/* Adapted from OpenCC. Copyright 2010-2026 Carbo Kuo and contributors. Apache-2.0. */
package io.github.opencckmp

data class ConfigLoadOptions(val includeTofuRiskDictionaries: Boolean = true)
data class ConfigWarning(val path: String, val message: String, val schemaPath: String = "", val keyword: String = "")

fun interface SegmentationFactory {
    fun create(properties: SegmentationProperties, resources: ResourceProvider): Segmentation
    /** Resolved provider names needed before synchronous initialization; hosts may apply option logic. */
    fun resourcesToPrepare(properties: SegmentationProperties, declaredResources: List<String>): List<String> = declaredResources
    /** Extensions with optional resources or fallback paths may perform their own ordered preparation. */
    suspend fun prepareResources(properties: SegmentationProperties, resources: AsyncResourceProvider,
                                 declaredResources: List<String>): ResourceProvider {
        val bytes = linkedMapOf<String, ByteArray>()
        for (name in resourcesToPrepare(properties, declaredResources).distinct()) bytes[name] = resources.read(name)
        return MemoryResourceProvider(bytes)
    }
}

/** Stateless loader. Optional caller-owned [dictionaryCache] shares dictionaries across loads. */
class Config(
    private val provider: ResourceProvider = ResourceProvider { throw FileNotFound(it) },
    readers: List<DictionaryReader> = standardDictionaryReaders,
    segmentationFactories: Map<String, SegmentationFactory> = emptyMap(),
    private val warningSink: ((ConfigWarning) -> Unit)? = null,
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
        val session = LoadSession(json, configDirectory, options, provider)
        check(session.advance() == null) { "Synchronous resource loading unexpectedly requested preparation" }
        return session.result!!
    }

    internal suspend fun prepareFromString(json: String, configDirectory: String, options: ConfigLoadOptions,
                                          read: suspend (String) -> ByteArray): Converter {
        val session = LoadSession(json, configDirectory, options, null)
        while (true) {
            val request = session.advance() ?: return session.result!!
            val factory = request.factory
            if (factory == null) {
                val bytes = mutableListOf<ByteArray>()
                for (name in request.names) bytes.add(read(name))
                session.provide(bytes)
            } else {
                session.providePlugin(factory.prepareResources(request.properties!!, AsyncResourceProvider(read), request.names))
            }
        }
    }

    /** An ordered load transaction. Only resource acquisition differs between sync/async drivers. */
    private inner class LoadSession(json: String, private val configDirectory: String,
                                    private val options: ConfigLoadOptions,
                                    private val pluginProvider: ResourceProvider?) {
        private val root = parseConfiguration(json)
        private val loaded = mutableMapOf<String, Dict>()
        private var next: (() -> Unit)? = { segmentation() }
        private var pending: LoadRequest? = null
        var result: Converter? = null
            private set

        init { if (warningSink != null) ConfigSchema.warning(root)?.let(warningSink) }

        fun advance(): LoadRequest? {
            check(pending == null) { "Resources must be supplied before advancing the load" }
            while (next != null) {
                val step = next!!
                next = null
                step()
            }
            check(pending != null || result != null) { "Incomplete configuration load" }
            return pending
        }

        fun provide(bytes: List<ByteArray>) {
            val request = checkNotNull(pending)
            check(bytes.size == request.names.size)
            pending = null
            next = { request.complete!!(bytes) }
        }

        fun providePlugin(resources: ResourceProvider) {
            val request = checkNotNull(pending)
            pending = null
            next = { request.completePlugin!!(resources) }
        }

        private fun request(names: List<String>, complete: (List<ByteArray>) -> Unit) {
            check(pending == null && next == null)
            pending = LoadRequest(names, complete)
        }

        private fun completeDictionary(dict: Dict?, complete: (Dict?) -> Unit) { next = { complete(dict) } }

        private fun parseDict(doc: JsonValue.Object, includeTofu: Boolean, complete: (Dict?) -> Unit) {
            val type = doc.string("type")
            if (type == "inline") {
                if (doc["may_output_tofu"] != null) throw InvalidFormat("Inline dictionary does not support may_output_tofu")
                val entries = doc.obj("entries").members.map { (key, value) ->
                    if (key.isEmpty()) throw InvalidFormat("Inline dictionary key must be a non-empty string")
                    val text = (value as? JsonValue.StringValue)?.value
                    if (text.isNullOrEmpty()) throw InvalidFormat("Inline dictionary value must be a non-empty string: ${key.substringBefore('\u0000')}")
                    // Upstream validates JSON lengths before constructing C-string dictionary entries.
                    DictEntry(key.substringBefore('\u0000'), listOf(text.substringBefore('\u0000')))
                }
                val lexicon = Lexicon(entries).sorted()
                for (index in 1 until lexicon.size) if (lexicon[index - 1].key == lexicon[index].key) {
                    throw InvalidFormat("Inline dictionary contains duplicate key: ${lexicon[index].key}")
                }
                completeDictionary(TextDict(lexicon), complete)
                return
            }
            val tofu = when (val value = doc["may_output_tofu"]) {
                null -> false
                is JsonValue.BooleanValue -> value.value
                else -> throw InvalidFormat("Property must be a boolean: may_output_tofu")
            }
            if (tofu && !includeTofu) { completeDictionary(null, complete); return }
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
                val values = doc.array("dicts").values
                val dicts = mutableListOf<Dict>()
                fun member(index: Int) {
                    if (index == values.size) { completeDictionary(if (dicts.isEmpty()) null else DictGroup(dicts, policy), complete); return }
                    val child = values[index] as? JsonValue.Object ?: throw InvalidFormat("Element of the array must be an object")
                    parseDict(child, includeTofu) { dict ->
                        if (dict != null) dicts.add(dict)
                        next = { member(index + 1) }
                    }
                }
                next = { member(0) }
                return
            }
            val file = resolveResource(configDirectory, doc.string("file"))
            val reader = readers[type] ?: throw InvalidFormat("Unknown dictionary type: $type")
            val identity = type + "\n" + file
            val existing = loaded[identity]
            if (existing != null) { completeDictionary(existing, complete); return }
            if (pluginProvider != null) dictionary(identity, file, reader, pluginProvider.read(file), complete)
            else request(listOf(file)) { bytes -> dictionary(identity, file, reader, bytes.single(), complete) }
        }

        private fun dictionary(identity: String, file: String, reader: DictionaryReader, bytes: ByteArray,
                               complete: (Dict?) -> Unit) {
            val dict = dictionaryCache?.load(cacheIdentity, file, bytes, reader) ?: reader.read(bytes)
            loaded[identity] = dict
            completeDictionary(dict, complete)
        }

        private fun chain(value: JsonValue.Array, complete: (ConversionChain) -> Unit) {
            val conversions = mutableListOf<Conversion>()
            fun step(index: Int) {
                if (index == value.values.size) { next = { complete(ConversionChain(conversions)) }; return }
                val doc = value.values[index] as? JsonValue.Object
                // Upstream ignores non-object conversion steps after advisory schema validation.
                if (doc == null) { next = { step(index + 1) }; return }
                parseDict(doc.obj("dict"), options.includeTofuRiskDictionaries) { dict ->
                    if (dict != null) conversions.add(Conversion(dict))
                    next = { step(index + 1) }
                }
            }
            next = { step(0) }
        }

        private fun segmentation() {
            val value = root["segmentation"]
            if (value == null) { main(null); return }
            val doc = value as? JsonValue.Object ?: throw InvalidFormat("Property must be an object: segmentation")
            val type = doc.string("type")
            if (type == "mmseg") {
                parseDict(doc.obj("dict"), true) { dict -> main(MaxMatchSegmentation(dict ?: TextDict(Lexicon(emptyList())))) }
                return
            }
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
            if (pluginProvider != null) { main(factory.create(pairs, pluginProvider)); return }
            pending = LoadRequest(declared, null, factory, pairs) { resources -> main(factory.create(pairs, resources)) }
        }

        private fun main(segmentation: Segmentation?) {
            chain(root.array("conversion_chain")) { chain ->
                val main = SingleStageConverter(segmentation, chain)
                val normalization = root["normalization"]
                if (normalization == null) result = main else {
                    val value = normalization as? JsonValue.Array ?: throw InvalidFormat("Property must be an array: normalization")
                    chain(value) { normalized -> result = ConfigBasedConverter(SingleStageConverter(null, normalized), main) }
                }
            }
        }
    }

    private class LoadRequest(val names: List<String>, val complete: ((List<ByteArray>) -> Unit)?,
                              val factory: SegmentationFactory? = null, val properties: SegmentationProperties? = null,
                              val completePlugin: ((ResourceProvider) -> Unit)? = null)

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
