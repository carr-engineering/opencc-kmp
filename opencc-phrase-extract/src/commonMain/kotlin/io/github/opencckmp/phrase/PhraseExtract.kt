/* Adapted from OpenCC PhraseExtract, Copyright 2015-2026 Carbo Kuo and contributors. Apache-2.0. */
package io.github.opencckmp.phrase

import io.github.opencckmp.ShouldNotBeHere
import io.github.opencckmp.UTF8Util
import kotlin.math.ln
import kotlin.math.sqrt

/** An owned, mutable extraction session. Length options count Unicode scalar values. */
class PhraseExtract {
    data class Signals(val frequency: Long, val cohesion: Double, val suffixEntropy: Double, val prefixEntropy: Double)
    private class MutableSignals(var frequency: Long = 0, var cohesion: Double = 0.0,
        var suffixEntropy: Double = 0.0, var prefixEntropy: Double = 0.0) {
        fun snapshot() = Signals(frequency, cohesion, suffixEntropy, prefixEntropy)
    }

    var wordMinLength: Int = 2
        set(value) { require(value >= 0); field = value }
    var wordMaxLength: Int = 2
        set(value) { require(value >= 0); field = value }
    var prefixSetLength: Int = 1
        set(value) { require(value >= 0); field = value }
    var suffixSetLength: Int = 1
        set(value) { require(value >= 0); field = value }
    /** Return true to exclude a candidate, matching upstream filter semantics. */
    var preCalculationFilter: (PhraseExtract, String) -> Boolean = ::defaultPreCalculationFilter
    var postCalculationFilter: (PhraseExtract, String) -> Boolean = ::defaultPostCalculationFilter

    private var text = ""
    private var boundaries = intArrayOf(0)
    private var suffixes = mutableListOf<String>()
    private var prefixes = mutableListOf<String>()
    private val statistics = mutableMapOf<String, MutableSignals>()
    private val candidates = mutableListOf<String>()
    private val selected = mutableListOf<String>()
    val words: List<String> get() = selected.toList()
    val wordCandidates: List<String> get() = candidates.toList()
    val extractedSuffixes: List<String> get() = suffixes.toList()
    val extractedPrefixes: List<String> get() = prefixes.toList()
    private var totalOccurrence = 0L
    private var logTotalOccurrence = 0.0
    private var suffixesExtracted = false
    private var prefixesExtracted = false
    private var frequenciesCalculated = false
    private var candidatesExtracted = false
    private var cohesionsCalculated = false
    private var prefixEntropiesCalculated = false
    private var suffixEntropiesCalculated = false

    fun setFullText(fullText: String) {
        UTF8Util.validate(fullText)
        text = fullText
        boundaries = offsets(fullText)
    }

    /** Execute upstream's complete pipeline. Call reset before reusing a populated session. */
    fun extract(fullText: String) {
        setFullText(fullText)
        extractSuffixes()
        calculateFrequency()
        calculateSuffixEntropy()
        releaseSuffixes()
        extractPrefixes()
        calculatePrefixEntropy()
        releasePrefixes()
        extractWordCandidates()
        calculateCohesions()
        selectWords()
    }

    /** Clear statistics/text, retaining length options and restoring the upstream default filters. */
    fun reset() {
        suffixesExtracted = false; prefixesExtracted = false; frequenciesCalculated = false
        candidatesExtracted = false; cohesionsCalculated = false
        prefixEntropiesCalculated = false; suffixEntropiesCalculated = false
        totalOccurrence = 0; logTotalOccurrence = 0.0
        releasePrefixes(); releaseSuffixes(); candidates.clear(); selected.clear(); statistics.clear()
        text = ""; boundaries = intArrayOf(0)
        preCalculationFilter = ::defaultPreCalculationFilter
        postCalculationFilter = ::defaultPostCalculationFilter
    }

    fun releaseSuffixes() { suffixes = mutableListOf() }
    fun releasePrefixes() { prefixes = mutableListOf() }

    fun extractSuffixes() {
        val limit = wordMaxLength.toLong() + suffixSetLength
        for (start in 0 until boundaries.lastIndex) {
            val end = minOf(boundaries.lastIndex.toLong(), start + limit).toInt()
            suffixes.add(text.substring(boundaries[start], boundaries[end]))
        }
        suffixes.sortWith { a, b -> UTF8Util.compare(a, b) }
        suffixesExtracted = true
    }

    fun extractPrefixes() {
        val limit = wordMaxLength.toLong() + prefixSetLength
        for (end in boundaries.lastIndex downTo 1) {
            val start = maxOf(0L, end - limit).toInt()
            prefixes.add(text.substring(boundaries[start], boundaries[end]))
        }
        prefixes.sortWith { a, b -> reverseCompare(a, b) }
        prefixesExtracted = true
    }

    fun calculateFrequency() {
        if (!suffixesExtracted) extractSuffixes()
        for (suffix in suffixes) {
            val indices = offsets(suffix)
            for (length in 1..minOf(indices.lastIndex, wordMaxLength)) {
                val word = suffix.substring(0, indices[length])
                statistics.getOrPut(word) { MutableSignals() }.frequency++
                totalOccurrence++
            }
        }
        logTotalOccurrence = ln(totalOccurrence.toDouble())
        frequenciesCalculated = true
    }

    fun extractWordCandidates() {
        if (!frequenciesCalculated) calculateFrequency()
        for (word in statistics.keys.sortedWith { a, b -> UTF8Util.compare(a, b) }) {
            if (offsets(word).lastIndex < wordMinLength || word.any { it in punctuation }) continue
            if (!preCalculationFilter(this, word)) candidates.add(word)
        }
        candidates.sortWith { a, b ->
            val frequencyOrder = frequency(b).compareTo(frequency(a))
            if (frequencyOrder != 0) frequencyOrder else UTF8Util.compare(a, b)
        }
        candidatesExtracted = true
    }

    fun calculateCohesions() {
        if (!candidatesExtracted) extractWordCandidates()
        for (word in candidates) {
            val indices = offsets(word)
            var minimum = Double.POSITIVE_INFINITY
            for (split in 1 until indices.lastIndex) {
                val pmi = logProbability(word) - logProbability(word.substring(0, indices[split])) -
                    logProbability(word.substring(indices[split]))
                minimum = minOf(pmi, minimum)
            }
            state(word).cohesion = minimum
        }
        cohesionsCalculated = true
    }

    fun calculateSuffixEntropy() {
        if (!suffixesExtracted) extractSuffixes()
        if (!frequenciesCalculated) calculateFrequency()
        calculateAdjacentEntropy(suffixes, suffixSetLength, suffix = true)
        suffixEntropiesCalculated = true
    }

    fun calculatePrefixEntropy() {
        if (!prefixesExtracted) extractPrefixes()
        if (!frequenciesCalculated) calculateFrequency()
        calculateAdjacentEntropy(prefixes, prefixSetLength, suffix = false)
        prefixEntropiesCalculated = true
    }

    private fun calculateAdjacentEntropy(slices: List<String>, setLength: Int, suffix: Boolean) {
        for (length in wordMinLength..wordMaxLength) {
            val adjacency = mutableMapOf<String, MutableMap<String, Long>>()
            for (slice in slices) {
                val indices = offsets(slice)
                val count = indices.lastIndex
                if (count < length) continue
                val word = if (suffix) slice.substring(0, indices[length]) else slice.substring(indices[count - length])
                val choices = adjacency.getOrPut(word) { mutableMapOf() }
                if (length.toLong() + setLength <= count) {
                    val neighbor = if (suffix) slice.substring(indices[length], indices[length + setLength])
                    else slice.substring(indices[count - length - setLength], indices[count - length])
                    choices[neighbor] = (choices[neighbor] ?: 0L) + 1
                }
            }
            for ((word, choices) in adjacency) {
                if (word.isEmpty()) continue
                val total = choices.values.sum().toDouble()
                var entropy = 0.0
                for (count in choices.values) {
                    val probability = count / total
                    entropy += probability * ln(probability)
                }
                if (entropy != 0.0) entropy = -entropy
                if (suffix) state(word).suffixEntropy = entropy else state(word).prefixEntropy = entropy
            }
        }
    }

    fun selectWords() {
        if (!candidatesExtracted) extractWordCandidates()
        if (!cohesionsCalculated) calculateCohesions()
        if (!prefixEntropiesCalculated) calculatePrefixEntropy()
        if (!suffixEntropiesCalculated) calculateSuffixEntropy()
        for (word in candidates) if (!postCalculationFilter(this, word)) selected.add(word)
    }

    private fun state(word: String): MutableSignals = statistics[word] ?: throw ShouldNotBeHere()
    fun signal(word: String): Signals = state(word).snapshot()
    fun cohesion(word: String): Double = state(word).cohesion
    fun entropy(word: String): Double = suffixEntropy(word) + prefixEntropy(word)
    fun suffixEntropy(word: String): Double = state(word).suffixEntropy
    fun prefixEntropy(word: String): Double = state(word).prefixEntropy
    fun frequency(word: String): Long = state(word).frequency
    fun probability(word: String): Double = frequency(word).toDouble() / totalOccurrence
    fun logProbability(word: String): Double = ln(frequency(word).toDouble()) - logTotalOccurrence

    companion object {
        private const val punctuation = " \n\r\t-,.?!*　，。、；：？！…“”「」—－（）《》．／＼"
        fun defaultPreCalculationFilter(extractor: PhraseExtract, word: String): Boolean = false
        fun defaultPostCalculationFilter(extractor: PhraseExtract, word: String): Boolean {
            val signals = extractor.signal(word)
            val logProbability = extractor.logProbability(word)
            val cohesionScore = signals.cohesion - logProbability * 0.5
            val entropyScore = sqrt(signals.prefixEntropy * (signals.suffixEntropy + 1)) - logProbability * 0.85
            return !(cohesionScore > 9 && entropyScore > 11 && signals.prefixEntropy > 0.5 &&
                signals.suffixEntropy > 0 && signals.prefixEntropy + signals.suffixEntropy > 3)
        }
        private fun offsets(text: String): IntArray {
            val result = IntArray(text.length + 1)
            var count = 0
            var position = 0
            while (position < text.length) {
                result[count++] = position
                position += if (text[position].isHighSurrogate()) 2 else 1
            }
            result[count++] = text.length
            return result.copyOf(count)
        }
        private fun reverseCompare(a: String, b: String): Int {
            var left = a.length
            var right = b.length
            while (left > 0 && right > 0) {
                left -= if (a[left - 1].isLowSurrogate()) 2 else 1
                right -= if (b[right - 1].isLowSurrogate()) 2 else 1
                val difference = UTF8Util.codePoint(a, left).compareTo(UTF8Util.codePoint(b, right))
                if (difference != 0) return difference
            }
            return left.compareTo(right)
        }
    }
}
