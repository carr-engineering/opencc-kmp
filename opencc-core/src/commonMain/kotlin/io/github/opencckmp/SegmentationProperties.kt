package io.github.opencckmp

/**
 * Immutable plugin options. Map lookup uses the first occurrence, matching bundled upstream helpers;
 * [orderedPairs] retains every option in original order for extensions with different lookup policies.
 * The loader's __config_dir precedes resource pairs, which precede ordinary plugin properties.
 */
class SegmentationProperties internal constructor(pairs: List<Pair<String, String>>) : AbstractMap<String, String>() {
    private val pairs = pairs.toList()
    private val first = linkedMapOf<String, String>().also { map ->
        for ((key, value) in pairs) if (!map.containsKey(key)) map[key] = value
    }
    val orderedPairs: List<Pair<String, String>> get() = pairs.toList()
    override fun get(key: String): String? = first[key]
    override val entries: Set<Map.Entry<String, String>> get() = first.map { (name, text) ->
        object : Map.Entry<String, String> { override val key = name; override val value = text }
    }.toSet()
}
