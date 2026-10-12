/* Pinned OpenCC 1.4.2 configuration schema, Apache-2.0. */
package io.github.opencckmp

/** Specialized validator for the pinned schema; no general JSON-schema dependency in the engine. */
internal object ConfigSchema {
    private class Fault(val path: String, val schema: String, val keyword: String)
    private fun token(name: String) = name.replace("~", "~0").replace("/", "~1")
    fun warning(root: JsonValue.Object): ConfigWarning? {
        val fault = validateRoot(root) ?: return null
        val path = fault.path.substringBefore('\u0000')
        val message = "config does not conform to schema: document path \"$path\", schema path \"${fault.schema}\", keyword \"${fault.keyword}\""
        return ConfigWarning(path, message, fault.schema, fault.keyword)
    }
    private fun validateRoot(doc: JsonValue.Object): Fault? {
        for ((name, value) in doc.members) {
            val path = "/" + token(name)
            val fault = when (name) {
                "name" -> if (value is JsonValue.StringValue) null else Fault(path, "/properties/name", "type")
                "normalization", "conversion_chain" -> chain(value, path, "/properties/$name")
                "segmentation" -> if (segmentation(value)) null else Fault(path, "/definitions/segmentation", "anyOf")
                else -> Fault(path, "", "additionalProperties")
            }
            if (fault != null) return fault
        }
        return if (doc["name"] == null || doc["conversion_chain"] == null) Fault("", "", "required") else null
    }
    private fun chain(value: JsonValue, path: String, schema: String): Fault? {
        if (value !is JsonValue.Array) return Fault(path, schema, "type")
        for ((index, step) in value.values.withIndex()) {
            val location = "$path/$index"
            if (step !is JsonValue.Object) return Fault(location, "/definitions/conversion", "type")
            for ((name, dict) in step.members) {
                if (name != "dict") return Fault(location + "/" + token(name), "/definitions/conversion", "additionalProperties")
                if (!dictionary(dict)) return Fault("$location/dict", "/definitions/dict", "anyOf")
            }
            if (step["dict"] == null) return Fault(location, "/definitions/conversion", "required")
        }
        return if (value.values.isEmpty()) Fault(path, schema, "minItems") else null
    }
    private fun dictionary(value: JsonValue): Boolean {
        if (value !is JsonValue.Object) return false
        fun variant(required: List<String>, property: (String, JsonValue) -> Boolean): Boolean =
            required.all { value[it] != null } && value.members.all { property(it.first, it.second) }
        return variant(listOf("type", "file")) { name, item -> when (name) {
            "type" -> stringIn(item, "text", "ocd", "ocd2")
            "file" -> nonEmptyString(item)
            "may_output_tofu" -> item is JsonValue.BooleanValue
            else -> false
        } } || variant(listOf("type", "entries")) { name, item -> when (name) {
            "type" -> stringIn(item, "inline")
            "entries" -> item is JsonValue.Object && item.members.all { nonEmptyString(it.second) }
            else -> false
        } } || variant(listOf("type", "dicts", "match_policy")) { name, item -> when (name) {
            "type" -> stringIn(item, "group")
            "dicts" -> item is JsonValue.Array && item.values.isNotEmpty() && item.values.all { dictionary(it) }
            "match_policy" -> stringIn(item, "short_circuit", "union")
            "may_output_tofu" -> item is JsonValue.BooleanValue
            else -> false
        } }
    }
    private fun segmentation(value: JsonValue): Boolean {
        if (value !is JsonValue.Object) return false
        val mmseg = value["type"] != null && value["dict"] != null && value.members.all { (name, item) -> when (name) {
            "type" -> stringIn(item, "mmseg")
            "dict" -> dictionary(item)
            else -> false
        } }
        val plugin = value["type"] != null && value.members.any { it.first == "type" && !stringIn(it.second, "mmseg") } && value.members.all { (name, item) -> when (name) {
            "type" -> nonEmptyString(item)
            "resources" -> item is JsonValue.Object && item.members.all { it.second is JsonValue.StringValue }
            else -> item is JsonValue.StringValue
        } }
        return mmseg || plugin
    }
    private fun nonEmptyString(value: JsonValue): Boolean = value is JsonValue.StringValue && value.value.isNotEmpty()
    private fun stringIn(value: JsonValue, vararg allowed: String): Boolean = value is JsonValue.StringValue && value.value in allowed
}
