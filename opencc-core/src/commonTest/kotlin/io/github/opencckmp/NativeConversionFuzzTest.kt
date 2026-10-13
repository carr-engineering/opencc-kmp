package io.github.opencckmp
import kotlin.test.*

class NativeConversionFuzzTest {
    @Test fun matchesReferenceLoopOnFuzzedUtf8() {
        fun dict(text: String) = TextDict.fromText(text)
        val group = DictGroup(listOf(dict("太后\t太后\n头发\t頭髮\n干燥\t乾燥\n鼠标\t鼠標\n"), dict("后\t后 後\n发\t發 髮\n干\t幹 乾 干\n里\t裏 里\n")))
        val conversions = listOf(Conversion(group), Conversion(MarisaDict.fromBytes(nativeConversionFuzzImage)))
        assertEquals(600, nativeConversionFuzzRows.size)
        for (row in nativeConversionFuzzRows) {
            val fields = row.split('|')
            val input = fields[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val conversion = conversions[fields[0].toInt()]
            if (fields[2] == "ERROR") assertFailsWith<InvalidUTF8>(row) { conversion.convertUtf8(input) }
            else assertContentEquals(fields[3].chunked(2).map { it.toInt(16).toByte() }.toByteArray(), conversion.convertUtf8(input), row)
        }
    }
}
