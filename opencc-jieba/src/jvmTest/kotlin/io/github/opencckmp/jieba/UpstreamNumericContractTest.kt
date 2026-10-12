package io.github.opencckmp.jieba

import java.io.File
import kotlin.test.*

class UpstreamNumericContractTest {
    @Test fun modelNumberTokensMatchPinnedCParser() {
        val root = System.getProperty("opencc.projectRoot") ?: error("Use upstreamJiebaConformance")
        val file = File(root, ".cache/conformance/jieba-numbers.tsv")
        for (line in file.readLines()) {
            val (encoded, expected) = line.split('\t')
            val token = encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray().decodeToString()
            assertEquals(expected.toULong(16).toLong(), cDouble(token).toBits(), token)
        }
    }
}
