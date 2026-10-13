package io.github.opencckmp.jieba

import kotlin.test.*

class NumericContractTest {
    @Test fun cNumericPrefixesAndWhitespace() {
        assertEquals(12.5, cDouble("\u000b\u000c+12.5tail"))
        assertEquals(100.0, cDouble("1e2suffix"))
        assertEquals(1.0, cDouble("1e+unfinished"))
        assertEquals(0.0, cDouble("text"))
        assertEquals(-12, cInt("\u000b\u000c-12tail"))
        assertTrue(cDouble("NaN(payload)").isNaN())
        assertEquals(Double.NEGATIVE_INFINITY, cDouble("-INFINITYsuffix"))
    }

    @Test fun hexadecimalIsPortableAndRoundToEven() {
        val cases = mapOf(
            "0x1p2tail" to 4.0,
            "-0X1.8p+2" to -6.0,
            "0xdead" to 57005.0,
            "0x1p-1074" to Double.fromBits(1),
            "0x1p-1075" to 0.0,
            "0x1.0000000000001p-1075" to Double.fromBits(1),
            "0x1.fffffffffffffp1023" to Double.MAX_VALUE,
            "0x1.fffffffffffff8p1023" to Double.POSITIVE_INFINITY,
            "0x1.00000000000008p0" to 1.0,
            "0x1.00000000000008001p0" to Double.fromBits(1.0.toBits() + 1),
            "0x0.fffffffffffff8p-1022" to Double.fromBits(1L shl 52),
            "-0x0p1000000000000" to -0.0,
            "0x1p1000000000000" to Double.POSITIVE_INFINITY,
            "0x1p-1000000000000" to 0.0,
            ("0x1" + "0".repeat(400) + "p-1600") to 1.0)
        for ((input, expected) in cases) assertEquals(expected.toBits(), cDouble(input).toBits(), input)
    }

    @Test fun mergedMetadataRequiresCompleteCNumber() {
        assertEquals(4.0, cDoubleComplete("\u000b0x1p2"))
        assertEquals(Double.POSITIVE_INFINITY, cDoubleComplete("inf"))
        assertTrue(assertNotNull(cDoubleComplete("nan(payload)")).isNaN())
        assertEquals(1.0, cDoubleComplete("1\u0000tail"))
        for (input in listOf("1 ", "1tail", "1e+", "0x", "", "\u00a01", "ınf"))
            assertNull(cDoubleComplete(input), input)
    }
}
