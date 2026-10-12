package io.github.opencckmp.detect

import io.github.opencckmp.*
import java.io.File
import kotlin.test.*

class UpstreamDataDetectionTest {
    @Test fun actualUpstreamCharacterAndRegionalDictionariesDriveDetection() {
        val root = File(System.getProperty("opencc.projectRoot") ?: error("Run upstreamDataDetection"))
        val requested = mutableListOf<String>()
        val resources = ResourceProvider { name -> requested.add(name); File(root, ".cache/oracle/data/$name").readBytes() }
        val source = ScriptDetector.fromResources(resources)
        assertEquals(ChineseScript.Simplified, source.detect("汉字转换").script)
        assertEquals(ChineseScript.Traditional, source.detect("漢字轉換").script)
        assertEquals(ChineseScript.Mixed, source.detect("汉字轉换").script)
        assertEquals(ChineseScript.Neutral, source.detect("中文 ABC 123 😀").script)
        assertEquals(TraditionalRegion.HongKong, source.detect("漢字轉換 綫").region)
        assertEquals(TraditionalRegion.Taiwan, source.detect("漢字轉換 裡").region)
        assertEquals(6, requested.size)
        requested.clear()
        val charactersOnly = ScriptDetector.fromResources(resources, includeRegions = false)
        assertEquals(TraditionalRegion.Generic, charactersOnly.detect("漢字轉換 綫").region)
        assertEquals(listOf("STCharacters.ocd2", "TSCharacters.ocd2"), requested)
    }
}
