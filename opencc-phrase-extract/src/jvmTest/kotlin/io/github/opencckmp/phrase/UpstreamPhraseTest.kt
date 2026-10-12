package io.github.opencckmp.phrase

import java.io.File
import kotlin.random.Random
import kotlin.test.*

class UpstreamPhraseTest {
    private val root = File(System.getProperty("opencc.projectRoot") ?: error("Run upstreamPhraseConformance"))
    @Test fun allCandidateStatisticsAndDefaultFiltersMatchNative() {
        val random = Random(142)
        val symbols = listOf("十", "四", "是", "人", "汉", "字", "天", "a", "1", "😀", "𠀀", "神", ".", "！", " ", "\n")
        val rarePhraseCorpus = buildString {
            repeat(100000) { append("背景") }
            repeat(64) { append((0x4e50 + it).toChar()); append("甲乙"); append((0x4ea0 + it).toChar()) }
        }
        val samples = listOf("", "四是四十是十十四是十四四十是四十", "😀𠀀😀𠀀", "一.二.三") +
            List(30) { buildString { repeat(100 + it * 31) { append(symbols.random(random)) } } } + rarePhraseCorpus
        var compared = 0
        var accepted = 0
        for ((index, text) in samples.withIndex()) {
            val min = 1 + index % 2
            val max = min + index % 3
            val prefix = index % 3
            val suffix = (index + 1) % 3
            val input = File(root, ".cache/probes/phrase-input.txt").apply { writeText(text) }
            val process = ProcessBuilder(File(root, ".cache/probes/phrase_probe").absolutePath,
                input.absolutePath, "$min", "$max", "$prefix", "$suffix").redirectErrorStream(true).start()
            val result = process.inputStream.bufferedReader().readLines()
            assertEquals(0, process.waitFor())
            val actual = PhraseExtract().apply {
                wordMinLength = min; wordMaxLength = max; prefixSetLength = prefix; suffixSetLength = suffix
                postCalculationFilter = { _, _ -> false }
                extract(text)
            }
            assertEquals(result.map { it.substringBefore('\t') }, actual.wordCandidates, "candidate order sample $index")
            assertEquals(actual.wordCandidates, actual.words)
            for (row in result) {
                val fields = row.split('\t')
                val word = fields[0]
                assertEquals(fields[1].toLong(), actual.frequency(word), word)
                val values = listOf(actual.cohesion(word), actual.prefixEntropy(word), actual.suffixEntropy(word),
                    actual.logProbability(word), actual.probability(word))
                for ((position, value) in values.withIndex()) {
                    val expected = if (fields[position + 2] == "inf") Double.POSITIVE_INFINITY else fields[position + 2].toDouble()
                    if (expected.isInfinite()) assertEquals(expected, value) else assertEquals(expected, value, 1e-12, "$index/$word/$position")
                }
                assertEquals(fields[7] == "1", !PhraseExtract.defaultPostCalculationFilter(actual, word), "filter $index/$word")
                if (fields[7] == "1") accepted++
                compared++
            }
        }
        assertTrue(compared > 5000)
        assertTrue(accepted > 0, "Corpus must exercise accepted default-filter results")
        println("Native phrase extraction: ${samples.size} corpora, $compared candidate statistics and filter decisions")
    }
}
