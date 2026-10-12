package io.github.opencckmp

/** High-level facade with the same conversion capabilities and Kotlin-owned return values. */
class SimpleConverter private constructor(internal val delegate: Converter) : Converter by delegate {
    constructor(
        configName: String,
        provider: ResourceProvider,
        options: ConfigLoadOptions = ConfigLoadOptions(),
        readers: List<DictionaryReader> = standardDictionaryReaders,
        dictionaryCache: DictionaryCache? = null,
        cacheIdentity: Any = provider,
        segmentationFactories: Map<String, SegmentationFactory> = emptyMap(),
    ) : this(Config(provider, readers, segmentationFactories, dictionaryCache = dictionaryCache, cacheIdentity = cacheIdentity)
        .newFromFile(configResourceName(configName), options))

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
    fun convertUtf8(input: ByteArray, startIndex: Int = 0, endIndex: Int = input.size,
                    mode: EncodedInputMode = EncodedInputMode.LengthDelimited): ByteArray? =
        context.capture { current().convertUtf8(input, startIndex, endIndex, mode) }
    fun convertTo(text: String, output: Appendable): Int? = context.capture { current().convertTo(text, output) }
    fun convertTo(text: String, output: CharArray, offset: Int = 0): Int? = context.capture { current().convertTo(text, output, offset) }
    fun convertUtf8To(input: ByteArray, output: ByteArray, offset: Int = 0,
                      startIndex: Int = 0, endIndex: Int = input.size,
                      mode: EncodedInputMode = EncodedInputMode.NullTerminated): Int? =
        context.capture { current().convertUtf8To(input, output, offset, startIndex, endIndex, mode) }
    fun close(): Boolean {
        converter = null
        return true
    }
}

/**
 * Prepare resource bytes through a host's suspend provider, then create a synchronous converter.
 * Shares sync validation/loading order and fetches only resources required by selected options.
 * No coroutine/network library is required by core. Plugin factories may refine their declared
 * resource preparation list according to options; initialization and conversion stay synchronous.
 */
suspend fun prepareConfig(
    configName: String,
    provider: AsyncResourceProvider,
    options: ConfigLoadOptions = ConfigLoadOptions(),
    readers: List<DictionaryReader> = standardDictionaryReaders,
    segmentationFactories: Map<String, SegmentationFactory> = emptyMap(),
    dictionaryCache: DictionaryCache? = null,
    cacheIdentity: Any = provider,
    warningSink: ((ConfigWarning) -> Unit)? = null,
): Converter {
    val name = configResourceName(configName).replace('\\', '/')
    val bytes = provider.read(name)
    val directory = name.substringBeforeLast('/', "")
    return Config(readers = readers, segmentationFactories = segmentationFactories, warningSink = warningSink,
        dictionaryCache = dictionaryCache, cacheIdentity = cacheIdentity)
        .prepareFromString(UTF8Util.decode(bytes), directory, options) { provider.read(it) }
}
