package io.github.opencckmp.cli

import io.github.opencckmp.*
import io.github.opencckmp.jieba.JiebaSegmentationFactory
import io.github.opencckmp.zip.ZipResourceProvider

internal fun loadConfig(args: ParsedArguments, host: CliHost, program: String): Converter {
    val name = args["config"]!!
    val paths = args.all("path").toMutableList()
    val executableDirectory = (host.executablePath ?: program).replace('\\', '/').substringBeforeLast('/', "")
    val packageDirectory = host.environment("OPENCC_PACKAGE_DATA_DIR")?.takeIf { it.isNotEmpty() }
        ?: executableDirectory.takeIf { it.isNotEmpty() }?.let { "$it/../share/opencc" }
    if (executableDirectory.isNotEmpty()) paths.add(executableDirectory)
    if (host.windows && executableDirectory.isNotEmpty()) paths.add(executableDirectory + "/../share/opencc")
    packageDirectory?.let { paths.add(it) }
    val envDirectory = host.environment("OPENCC_DATA_DIR")
    fun file(path: String): ByteArray? = try { host.openInput(path).use(::readAll) } catch (_: FileNotFound) { null }
    var sourceName = name
    var bytes: ByteArray? = null
    var bundle = false
    val zip = args.zip?.let { ZipResourceProvider(host.openInput(it).use(::readAll), it) }
    if (zip != null) try {
        val resource = zip.getResource(name); sourceName = resource.name; bytes = resource.bytes
    } catch (_: FileNotFound) { }
    if (bytes == null) {
        bytes = file(name)
        if (bytes == null) {
            if (!packageDirectory.isNullOrEmpty()) for (candidate in listOf(join(packageDirectory, name), join(packageDirectory, name + ".json"))) {
                val found = file(candidate)
                if (found != null) { sourceName = candidate; bytes = found; break }
            }
        }
        if (bytes == null && host.bundledResources != null) for (candidate in listOf(name, name + ".json")) try {
            bytes = host.bundledResources!!.read(candidate); sourceName = candidate; bundle = true; break
        } catch (_: FileNotFound) { }
        if (bytes == null) for (directory in paths + listOfNotNull(envDirectory)) {
            val candidate = join(directory, name)
            val found = file(candidate)
            if (found != null) { sourceName = candidate; bytes = found; break }
        }
    }
    val contents = bytes ?: throw FileNotFound(name)
    val directory = sourceName.replace('\\', '/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    val roots = listOf(directory, ".").filter { it.isNotEmpty() } + paths
    val provider = if (zip != null) zip else ResourceProvider { requested ->
        val raw = if (directory.isNotEmpty() && requested.startsWith(directory)) requested.removePrefix(directory) else requested
        var result: ByteArray? = null
        if (bundle) try { result = host.bundledResources?.read(raw) } catch (_: FileNotFound) { }
        if (result == null) {
            val candidates = if (absolute(raw)) listOf(raw) else roots.map { join(it, raw) }
            for (candidate in candidates) { result = file(candidate); if (result != null) break }
        }
        result ?: throw FileNotFound(if (absolute(raw)) raw else "$raw (searched: ${roots.joinToString(", ")})")
    }
    val warnings: (ConfigWarning) -> Unit = { warning -> host.message(
        "warning: config does not conform to schema: document path \"${warning.path}\", schema path \"${warning.schemaPath}\", keyword \"${warning.keyword}\"\n") }
    return Config(provider, segmentationFactories = mapOf("jieba" to JiebaSegmentationFactory()), warningSink = warnings)
        .newFromString(UTF8Util.decode(contents), directory, ConfigLoadOptions(args.flag("include-tofu-risk-dictionaries")))
}
private fun absolute(path: String): Boolean = path.startsWith('/') || path.startsWith('\\') || path.length > 1 && path[1] == ':'
private fun join(directory: String, path: String): String = if (absolute(path) || directory.isEmpty()) path else directory.trimEnd('/', '\\') + "/" + path
