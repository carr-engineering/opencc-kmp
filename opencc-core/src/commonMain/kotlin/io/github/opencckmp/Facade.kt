package io.github.opencckmp

/** High-level facade with the same conversion capabilities and Kotlin-owned return values. */
class SimpleConverter private constructor(private val delegate: Converter) : Converter by delegate {
    constructor(
        configName: String,
        provider: ResourceProvider,
        options: ConfigLoadOptions = ConfigLoadOptions(),
        readers: List<DictionaryReader> = standardDictionaryReaders,
    ) : this(Config(provider, readers).newFromFile(configResourceName(configName), options))

    companion object {
        fun fromConfig(converter: Converter): SimpleConverter = SimpleConverter(converter)
    }
}

internal fun configResourceName(name: String): String = if (name.endsWith(".json")) name else "$name.json"

/** Caller-owned C-style context. Last error is scoped to this context, with no global state. */
class OpenCC(private val loader: Config) {
    var lastError: OpenCCException? = null
        private set

    fun open(configName: String = "s2t"): OpenCCHandle? = capture {
        OpenCCHandle(loader.newFromFile(configResourceName(configName)), this)
    }
    fun error(): String? = lastError?.message
    internal fun <T> capture(block: () -> T): T? = try {
        block()
    } catch (error: OpenCCException) {
        lastError = error
        null
    }
}

/** Mutable handle; callers serialize close/error operations. Closing drops only this handle's reference. */
class OpenCCHandle internal constructor(converter: Converter, private val context: OpenCC) {
    private var converter: Converter? = converter
    private fun current(): Converter = converter ?: throw OpenCCException("Converter handle is closed")
    fun convert(text: String): String? = context.capture { current().convert(text) }
    fun convert(text: String, startIndex: Int, endIndex: Int): String? =
        context.capture { current().convert(text, startIndex, endIndex) }
    fun convertUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size): ByteArray? =
        context.capture { current().convertUtf8(input, startIndex, endIndex) }
    fun convertTo(text: String, output: Appendable): Int? = context.capture { current().convertTo(text, output) }
    fun convertTo(text: String, output: CharArray, offset: Int = 0): Int? = context.capture { current().convertTo(text, output, offset) }
    fun convertUtf8To(input: ByteArray, output: ByteArray, offset: Int = 0): Int? =
        context.capture { current().convertUtf8To(input, output, offset) }
    fun close(): Boolean {
        converter = null
        return true
    }
}

/**
 * Prepare resource bytes through a host's suspend provider, then create a synchronous converter.
 * Fetches the full referenced closure, including optional dictionaries. No coroutine/network library
 * is required by core. Segmentation-extension resources are included when named in config resources.
 */
suspend fun prepareConfig(
    configName: String,
    provider: AsyncResourceProvider,
    options: ConfigLoadOptions = ConfigLoadOptions(),
    readers: List<DictionaryReader> = standardDictionaryReaders,
    segmentationFactories: Map<String, SegmentationFactory> = emptyMap(),
): Converter {
    val name = configResourceName(configName).replace('\\', '/')
    val bytes = provider.read(name)
    val root = JsonParser(UTF8Util.decode(bytes)).parse()
    val directory = name.substringBeforeLast('/', "")
    val names = linkedSetOf<String>()
    fun collect(value: JsonValue) {
        when (value) {
            is JsonValue.Object -> {
                (value["file"] as? JsonValue.StringValue)?.value?.let { names.add(resolveResource(directory, it)) }
                (value["resources"] as? JsonValue.Object)?.members?.forEach { (_, resource) ->
                    (resource as? JsonValue.StringValue)?.value?.let { names.add(resolveResource(directory, it)) }
                }
                value.members.forEach { collect(it.second) }
            }
            is JsonValue.Array -> value.values.forEach { collect(it) }
            else -> Unit
        }
    }
    collect(root)
    val prepared = mutableMapOf(name to bytes)
    for (resource in names) if (!prepared.containsKey(resource)) prepared[resource] = provider.read(resource)
    return Config(MemoryResourceProvider(prepared), readers, segmentationFactories).newFromFile(name, options)
}
