/* Kotlin value/range equivalents of OpenCC ver.1.4.2 UTF8StringSliceTest.cpp assertions. Apache-2.0. */
package io.github.opencckmp

import kotlin.test.*

class NativeStringSliceAssertionsTest {
    private val text = "天行健，君子以自強不息。地勢坤，君子以厚德載物。"
    @Test fun utf8Length() { assertEquals(0, UTF8Util.length("")); assertEquals(24, UTF8Util.length(text)) }
    @Test fun byteLength() { assertEquals(0, UTF8Util.byteLength("")); assertEquals(72, UTF8Util.byteLength(text)) }
    @Test fun left() { assertEquals("天行健", text.substring(0, 3)) }
    @Test fun right() { assertEquals("厚德載物。", text.takeLast(5)) }
    @Test fun subString() { assertEquals("自強不息", text.substring(7, 11)) }
    @Test fun toStringValue() { assertEquals("地勢坤", text.substring(12, 15)) }
    @Test fun compare() { assertTrue(UTF8Util.compare(text.substring(12, 15), "一") > 0); assertEquals("地勢坤", text.substring(12, 15)) }
    @Test fun moveRight() {
        var start = UTF8Util.nextIndex(text, 0)
        assertEquals("行健，君子以自強不息。地勢坤，君子以厚德載物。", text.substring(start))
        repeat(23) { start = UTF8Util.nextIndex(text, start) }
        assertEquals("", text.substring(start))
        if (start < text.length) start = UTF8Util.nextIndex(text, start)
        assertEquals("", text.substring(start))
    }
    @Test fun moveLeft() {
        var end = UTF8Util.previousIndex(text, text.length)
        assertEquals("天行健，君子以自強不息。地勢坤，君子以厚德載物", text.substring(0, end))
        repeat(22) { end = UTF8Util.previousIndex(text, end) }
        assertEquals("天", text.substring(0, end))
        end = UTF8Util.previousIndex(text, end)
        if (end > 0) end = UTF8Util.previousIndex(text, end)
        assertEquals("", text.substring(0, end))
    }
    @Test fun reverseCompare() {
        for ((left, right, expected) in listOf(Triple("", "", 0), Triple("", "大", -1), Triple("一", "二", -1),
            Triple("z一", "a二", -1), Triple("一一", "一", 1))) assertEquals(expected, UTF8Util.reverseCompare(left, right))
    }
    @Test fun findBytePosition() {
        fun offset(source: String, pattern: String): Int = source.indexOf(pattern).let { if (it < 0) -1 else UTF8Util.byteLength(source.substring(0, it)) }
        assertEquals(0, offset(text, "")); assertEquals(0, offset(text, "天")); assertEquals(9, offset(text, "，"))
        assertEquals(-1, offset(text, "a")); assertEquals(-1, offset(text, "\n")); assertEquals(3, offset("了。", "。"))
    }
}
