package io.github.opencckmp.interop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InteropTest {
    private val inline = """{"conversion_chain":[{"dict":{"type":"inline","entries":{"汉":"漢","转换":"轉換"}}}]}"""
    @Test fun ownedResourcesAndSurrogateRanges() {
        val bytes = inline.encodeToByteArray()
        val resources = OpenCCResources()
        resources.put("config.json", bytes)
        bytes.fill(0)
        val session = resources.load("config", true)
        resources.clear()
        assertEquals("😀漢字轉換", session.convert("😀汉字转换"))
        assertEquals("漢", session.convertRange("😀汉",2,3))
        assertFailsWith<IllegalArgumentException> { session.convertRange("😀汉",1,3) }
        assertEquals("漢", session.convertUtf8("汉".encodeToByteArray()).decodeToString())
        val mapped = session.convertWithSegments("😀汉")
        assertEquals(3,mapped.segments.last().sourceEnd)
        assertEquals(3,mapped.segments.last().targetEnd)
    }
    @Test fun inspectionAndIndependentStreamLifetime() {
        val session = OpenCCResources().loadJson(inline,"",true)
        val inspected = session.inspect("汉转换")
        assertEquals("漢轉換", inspected.output)
        assertEquals(1, inspected.stages.size)
        val stream = session.stream(16)
        assertEquals("",stream.convertChunk("汉转"))
        session.close()
        assertFailsWith<IllegalStateException> { session.convert("汉") }
        assertEquals("漢轉換",stream.finish("换"))
        assertEquals("",stream.finish(""))
    }
    @Test fun binaryDictionaryAndMissingResourceErrors() {
        val resources = OpenCCResources()
        resources.put("dict.txt","汉\t漢\n".encodeToByteArray())
        val session = resources.loadJson("""{"conversion_chain":[{"dict":{"type":"text","file":"dict.txt"}}]}""","",true)
        assertEquals("漢",session.convert("汉"))
        assertTrue(resources.remove("dict.txt"))
        assertFailsWith<Exception> { resources.load("missing",true) }
    }

    @Test fun literalFilenamesWinAndBareAliasesFallBackOnlyForMissingRoots() {
        val resources = OpenCCResources()
        for (name in listOf("settings.conf", "custom/config", "./literal", "")) {
            resources.putText(name, inline)
            assertEquals("漢", resources.load(name, true).convert("汉"))
        }
        resources.putText("literal", inline)
        resources.putText("literal.json", inline.replace("漢", "wrong"))
        assertEquals("漢", resources.load("literal", true).convert("汉"))
        resources.putText("alias.json", inline)
        assertEquals("漢", resources.load("alias", true).convert("汉"))
        for (name in listOf("missing.conf", "custom/missing")) {
            val error = assertFailsWith<io.github.opencckmp.FileNotFound> { resources.load(name, true) }
            assertEquals(name, error.resourceName)
        }
    }

    @Test fun rootParseAndDictionaryFailuresNeverRetryJsonAlias() {
        val resources = OpenCCResources()
        resources.putText("bad", "broken")
        resources.putText("bad.json", inline)
        assertFailsWith<io.github.opencckmp.InvalidFormat> { resources.load("bad", true) }
        resources.putText("literal", """{"conversion_chain":[{"dict":{"type":"text","file":"missing.txt"}}]}""")
        resources.putText("literal.json", inline)
        val error = assertFailsWith<io.github.opencckmp.FileNotFound> { resources.load("literal", true) }
        assertEquals("missing.txt", error.resourceName)
    }
}
