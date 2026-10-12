package io.github.opencckmp.detect

import io.github.opencckmp.*

enum class ChineseScript { Simplified, Traditional, Mixed, Neutral }
enum class DetectionConfidence { Low, High }
enum class TraditionalRegion { HongKong, Taiwan, Generic }
data class DetectionThresholds(val simplifiedRatio: Double = 0.8, val traditionalRatio: Double = 0.2,
    val minimumDistinguishingCharacters: Int = 3) {
    init {
        require(traditionalRatio in 0.0..1.0 && simplifiedRatio in traditionalRatio..1.0)
        require(minimumDistinguishingCharacters >= 0)
    }
}
data class ScriptDetection(val script: ChineseScript, val confidence: DetectionConfidence,
    val simplifiedCount: Int, val traditionalCount: Int, val region: TraditionalRegion? = null,
    val hongKongScore: Int = 0, val taiwanScore: Int = 0)

/** Optional Kotlin extra, not an upstream language-identification API. Instances are immutable. */
class ScriptDetector(
    stCharacters: Dict,
    tsCharacters: Dict,
    hkVariants: Dict? = null,
    twVariants: Dict? = null,
    hkPhrases: Dict? = null,
    twPhrases: Dict? = null,
    val thresholds: DetectionThresholds = DetectionThresholds(),
) {
    private val simplified: Set<String>
    private val traditional: Set<String>
    private val hk: Dict
    private val tw: Dict
    init {
        val st = stCharacters.lexicon.entries
        val ts = tsCharacters.lexicon.entries
        simplified = st.map { it.key }.toSet() - ts.map { it.key }.toSet() - st.flatMap { it.values }.toSet()
        traditional = ts.map { it.key }.toSet() - st.map { it.key }.toSet() - ts.flatMap { it.values }.toSet()
        hk = regionalDictionary(hkVariants, hkPhrases)
        tw = regionalDictionary(twVariants, twPhrases)
    }

    fun detect(text: String): ScriptDetection {
        UTF8Util.validate(text)
        var s = 0
        var t = 0
        var position = 0
        while (position < text.length) {
            val end = UTF8Util.nextIndex(text, position)
            val character = text.substring(position, end)
            if (character in simplified) s++
            if (character in traditional) t++
            position = end
        }
        val total = s + t
        val ratio = if (total == 0) 0.0 else s.toDouble() / total
        val script = when {
            total == 0 -> ChineseScript.Neutral
            ratio >= thresholds.simplifiedRatio -> ChineseScript.Simplified
            ratio <= thresholds.traditionalRatio -> ChineseScript.Traditional
            else -> ChineseScript.Mixed
        }
        val confidence = if (total < thresholds.minimumDistinguishingCharacters) DetectionConfidence.Low else DetectionConfidence.High
        if (script != ChineseScript.Traditional) return ScriptDetection(script, confidence, s, t)
        val hongKong = regionalScore(hk, text)
        val taiwan = regionalScore(tw, text)
        val region = when {
            hongKong > taiwan -> TraditionalRegion.HongKong
            taiwan > hongKong -> TraditionalRegion.Taiwan
            else -> TraditionalRegion.Generic
        }
        return ScriptDetection(script, confidence, s, t, region, hongKong, taiwan)
    }

    private fun regionalDictionary(vararg dictionaries: Dict?): Dict {
        val values = dictionaries.filterNotNull().flatMap { it.lexicon.entries }.flatMap { it.values }.filter { it.isNotEmpty() }.toSet()
        return TextDict(Lexicon(values.map { DictEntry(it, listOf(it)) }).sorted())
    }

    private fun regionalScore(dictionary: Dict, text: String): Int {
        var score = 0
        var position = 0
        while (position < text.length) {
            val match = dictionary.matchPrefix(text, position)
            if (match == null) position = UTF8Util.nextIndex(text, position) else {
                score++
                position += match.key.length
            }
        }
        return score
    }

    companion object {
        /** Explicitly prepares two character dictionaries plus four regional dictionaries if requested. */
        fun fromResources(provider: ResourceProvider, includeRegions: Boolean = true,
            thresholds: DetectionThresholds = DetectionThresholds()): ScriptDetector {
            fun load(name: String): Dict = MarisaDict.fromBytes(provider.read("$name.ocd2"))
            return ScriptDetector(load("STCharacters"), load("TSCharacters"),
                if (includeRegions) load("HKVariants") else null,
                if (includeRegions) load("TWVariants") else null,
                if (includeRegions) load("HKPhrases") else null,
                if (includeRegions) load("TWPhrases") else null, thresholds)
        }
    }
}
