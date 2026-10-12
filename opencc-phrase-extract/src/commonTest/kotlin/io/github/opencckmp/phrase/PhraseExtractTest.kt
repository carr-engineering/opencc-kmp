package io.github.opencckmp.phrase

import io.github.opencckmp.InvalidUTF16
import io.github.opencckmp.ShouldNotBeHere
import kotlin.math.ln
import kotlin.test.*

class PhraseExtractTest {
    private val text = "四是四十是十十四是十四四十是四十"
    private fun extractor() = PhraseExtract().apply { wordMinLength = 1; wordMaxLength = 3; setFullText(text) }
    private fun near(expected: Double, actual: Double) = assertEquals(expected, actual, 1e-12)

    @Test fun nativeSuffixAndPrefixSequences() {
        val source = extractor()
        source.extractSuffixes()
        assertEquals(listOf("十", "十十四是", "十四四十", "十四是十", "十是十十", "十是四十", "四十", "四十是十", "四十是四",
            "四四十是", "四是十四", "四是四十", "是十十四", "是十四四", "是四十", "是四十是"), source.extractedSuffixes)
        source.extractPrefixes()
        assertEquals(listOf("十是十十", "十四四十", "十是四十", "四是四十", "四十是十", "十四是十", "四", "是十十四",
            "四是十四", "是十四四", "四十是四", "四是四", "四四十是", "是四十是", "四是", "十十四是"), source.extractedPrefixes)
    }

    @Test fun nativeFrequenciesProbabilitiesAndCandidateOrdering() {
        val source = extractor()
        source.calculateFrequency()
        for ((word, count) in mapOf("四" to 6L, "十" to 6L, "是" to 4L, "四十" to 3L, "是四十" to 2L, "是四" to 2L, "四是" to 2L))
            assertEquals(count, source.frequency(word))
        near(-2.0149030205422647, source.logProbability("四"))
        near(-2.4203681286504288, source.logProbability("是"))
        near(-2.7080502011022096, source.logProbability("四十"))
        near(-3.8066624897703196, source.logProbability("是十十"))
        near(source.logProbability("四"), ln(source.probability("四")))
        source.extractWordCandidates()
        assertEquals(listOf("十", "四", "是", "四十", "十四", "十是", "四十是", "四是", "是十", "是四", "是四十", "十十", "十十四",
            "十四四", "十四是", "十是十", "十是四", "四四", "四四十", "四是十", "四是四", "是十十", "是十四"), source.wordCandidates)
    }

    @Test fun nativeCohesionsAndBothEntropies() {
        val source = extractor()
        source.calculateCohesions()
        assertEquals(Double.POSITIVE_INFINITY, source.cohesion("四"))
        near(1.3217558399823193, source.cohesion("四十"))
        near(0.91629073187415511, source.cohesion("十四"))
        for (word in listOf("十是", "四是四", "十是十")) near(1.3217558399823193, source.cohesion(word))
        source.calculateSuffixEntropy()
        near(1.0549201679861442, source.suffixEntropy("十"))
        near(1.0114042647073518, source.suffixEntropy("四"))
        near(0.69314718055994529, source.suffixEntropy("十四"))
        near(0.69314718055994529, source.suffixEntropy("十是"))
        source.calculatePrefixEntropy()
        near(1.0114042647073516, source.prefixEntropy("十"))
        near(1.0549201679861442, source.prefixEntropy("四"))
        near(0.69314718055994529, source.prefixEntropy("十四"))
        near(0.63651416829481278, source.prefixEntropy("四十"))
        for (word in listOf("四是四", "十是十")) {
            near(0.0, source.prefixEntropy(word)); near(0.0, source.suffixEntropy(word))
        }
        near(source.prefixEntropy("四") + source.suffixEntropy("四"), source.entropy("四"))
    }

    @Test fun nativeFiltersSelectionPunctuationAndReset() {
        val source = extractor()
        source.postCalculationFilter = { extractor, word -> extractor.frequency(word) == 1L }
        source.selectWords()
        assertEquals(listOf("十", "四", "是", "四十", "十四", "十是", "四十是", "四是", "是十", "是四", "是四十"), source.words)
        source.reset()
        assertEquals(3, source.wordMaxLength)
        assertTrue(source.words.isEmpty())
        assertFailsWith<ShouldNotBeHere> { source.signal("四") }
        source.wordMaxLength = 2
        source.setFullText("一.二.三")
        source.extractPrefixes()
        assertEquals(listOf("一.", ".二.", "一", "二.三", "一.二"), source.extractedPrefixes)
        source.extractWordCandidates()
        assertTrue(source.wordCandidates.all { '.' !in it })
        source.preCalculationFilter = { _, word -> word == "一" }
        source.reset()
        assertFalse(source.preCalculationFilter(source, "一"))
    }

    @Test fun fullPipelineSupplementaryCharactersAndEmptyInput() {
        val source = PhraseExtract().apply { wordMinLength = 1; wordMaxLength = 3; postCalculationFilter = { _, _ -> false } }
        source.extract("😀𠀀😀𠀀")
        assertEquals(2L, source.frequency("😀𠀀"))
        assertTrue("😀𠀀😀" in source.words)
        assertTrue(source.extractedSuffixes.isEmpty())
        assertTrue(source.extractedPrefixes.isEmpty())
        source.reset()
        source.extract("")
        assertTrue(source.wordCandidates.isEmpty())
        assertTrue(source.words.isEmpty())
        assertFailsWith<InvalidUTF16> { source.setFullText("\uD800") }
    }
}
