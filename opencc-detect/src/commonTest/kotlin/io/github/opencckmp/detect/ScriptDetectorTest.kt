package io.github.opencckmp.detect

import io.github.opencckmp.*
import kotlin.test.*

class ScriptDetectorTest {
    private fun dictionary(text: String) = TextDict.fromText(text)
    private fun detector(thresholds: DetectionThresholds = DetectionThresholds()) = ScriptDetector(
        dictionary("汉\t漢\n龙\t龍\n马\t馬\n里\t里 裏\n𠀀\t𠀁\n"),
        dictionary("漢\t汉\n龍\t龙\n馬\t马\n裏\t里\n𠀁\t𠀀\n"),
        dictionary("線\t綫\n"), dictionary("裏\t裡\n"), dictionary("軟件\t軟件\n"), dictionary("軟件\t軟體\n"), thresholds)

    @Test fun exactSetRulesCountsAndThresholdBoundaries() {
        val source = detector()
        assertEquals(ChineseScript.Neutral, source.detect("里 ABC 123 😀").script)
        val s = source.detect("汉龙马里")
        assertEquals(ChineseScript.Simplified, s.script)
        assertEquals(3, s.simplifiedCount)
        assertEquals(DetectionConfidence.High, s.confidence)
        val t = source.detect("漢龍馬")
        assertEquals(ChineseScript.Traditional, t.script)
        assertEquals(3, t.traditionalCount)
        assertEquals(ChineseScript.Mixed, source.detect("汉漢").script)
        assertEquals(DetectionConfidence.Low, source.detect("汉漢").confidence)
        assertEquals(ChineseScript.Simplified, source.detect("汉汉汉汉漢").script)
        assertEquals(ChineseScript.Traditional, source.detect("汉漢漢漢漢").script)
        assertEquals(1, source.detect("𠀀😀").simplifiedCount)
        assertEquals(1, source.detect("𠀁😀").traditionalCount)
    }

    @Test fun regionalScoresUseVariantsAndPhrasesWithGenericTies() {
        val source = detector()
        assertEquals(TraditionalRegion.Generic, source.detect("漢龍馬").region)
        assertEquals(TraditionalRegion.HongKong, source.detect("漢龍馬綫軟件").region)
        assertEquals(TraditionalRegion.Taiwan, source.detect("漢龍馬裡軟體").region)
        assertEquals(TraditionalRegion.Generic, source.detect("漢龍馬綫裡").region)
        assertNull(source.detect("汉龙马").region)
    }

    @Test fun thresholdsAreConfigurableAndMalformedTextHasTypedErrors() {
        val source = detector(DetectionThresholds(0.6, 0.4, 1))
        assertEquals(ChineseScript.Simplified, source.detect("汉汉汉漢漢").script)
        assertEquals(DetectionConfidence.High, source.detect("汉").confidence)
        assertFailsWith<IllegalArgumentException> { DetectionThresholds(0.2, 0.8) }
        assertFailsWith<InvalidUTF16> { source.detect(charArrayOf(0xD800.toChar()).concatToString()) }
    }
}
