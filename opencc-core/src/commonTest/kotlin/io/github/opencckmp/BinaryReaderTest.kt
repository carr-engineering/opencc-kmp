package io.github.opencckmp

import kotlin.test.*

class BinaryReaderTest {
    private val expected = mapOf("a" to listOf("A", "B"), "ab" to listOf("X"), "abc" to listOf("Y", "Z"),
        "long_shared_prefix_one_suffix" to listOf("one"), "long_shared_prefix_two_suffix" to listOf("two"),
        "汉" to listOf("漢"), "汉字" to listOf("漢字"), "词" to listOf("詞"), "⿰龙马" to listOf("龘"),
        "😀" to listOf("smile"), "𠀀" to listOf("甲", "乙"))

    @Test fun nativeImagesDecodeOnEveryKotlinTarget() {
        for (dict in listOf(DartsDict.fromBytes(BinaryTestImages.ocd), MarisaDict.fromBytes(BinaryTestImages.ocd2))) {
            assertEquals(expected, dict.lexicon.entries.associate { it.key to it.values })
            for ((key, values) in expected) {
                assertEquals(values, dict.match(key)?.values)
                assertEquals(key, dict.matchPrefix(key + "!")?.key)
            }
            assertEquals(listOf("abc", "ab", "a"), dict.matchAllPrefixes("abcd").map { it.key })
            assertNull(dict.match("abcd"))
        }
    }

    @Test fun legacyAndBigEndianDartsImagesDecode() {
        val original = BinaryTestImages.ocd
        // Transform only numeric fields of a verified native image; string buffers are unchanged.
        fun transform(width: Int, order: DictionaryByteOrder): ByteArray {
            val source = BinaryCursor(original, DictionaryByteOrder.LittleEndian, 12)
            val output = mutableListOf<Byte>().apply { addAll(original.take(12)) }
            fun write(value: Long) {
                repeat(width) { index ->
                    val shift = if (order == DictionaryByteOrder.LittleEndian) index * 8 else (width - index - 1) * 8
                    output.add((value ushr shift).toByte())
                }
            }
            val arrayBytes = source.count()
            write((arrayBytes / 4 * width).toLong())
            repeat(arrayBytes / 4) { write(source.integer(4)) }
            val entries = source.count()
            write(entries.toLong())
            repeat(2) {
                val bytes = source.count()
                write(bytes.toLong())
                output.addAll(source.take(bytes).toList())
            }
            repeat(entries) {
                val values = source.count()
                write(values.toLong())
                write(source.integer(4))
                repeat(values) { write(source.integer(4)) }
            }
            return output.toByteArray()
        }
        for (order in DictionaryByteOrder.entries) for (width in listOf(4, 8)) {
            val image = transform(width, order)
            assertEquals(expected, DartsDict.fromBytes(image).lexicon.entries.associate { it.key to it.values }, "$order/$width")
            assertEquals("abc", DartsDict.fromBytes(image, order).matchPrefix("abcd")?.key)
        }
    }

    @Test fun malformedBinaryInputHasTypedErrors() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2, 3))) {
            assertFailsWith<InvalidFormat> { MarisaDict.fromBytes(bytes) }
            assertFailsWith<InvalidFormat> { DartsDict.fromBytes(bytes) }
        }
        assertFailsWith<InvalidFormat> { MarisaDict.fromBytes(BinaryTestImages.ocd2.dropLast(1).toByteArray()) }
        assertFailsWith<InvalidFormat> { DartsDict.fromBytes(BinaryTestImages.ocd.dropLast(1).toByteArray()) }
    }
}
