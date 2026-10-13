package io.github.opencckmp

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FacadeResourceNameTest {
    private fun config(value: String): ByteArray =
        """{"conversion_chain":[{"dict":{"type":"inline","entries":{"汉":"$value"}}}]}""".encodeToByteArray()

    @Test fun explicitFilenamesAndPathsStayLiteralAcrossFacades() {
        for (name in listOf("settings.conf", "custom/config", "custom/config.json", "./literal", "")) {
            val reads = mutableListOf<String>()
            val provider = ResourceProvider { requested ->
                reads.add(requested)
                if (requested == name) config("漢") else throw FileNotFound(requested)
            }
            assertEquals("漢", SimpleConverter(name, provider).convert("汉"))
            assertEquals("漢", assertNotNull(OpenCC(Config(provider)).open(name)).convert("汉"))
            assertEquals(listOf(name, name), reads)
        }
    }

    @Test fun dottedAndPathFailuresPreserveExactNamesWithoutSuffixRetry() {
        for (name in listOf("missing.conf", "custom/missing", "C:/missing")) {
            val reads = mutableListOf<String>()
            val provider = ResourceProvider { reads.add(it); throw FileNotFound(it) }
            assertEquals(name, assertFailsWith<FileNotFound> { SimpleConverter(name, provider) }.resourceName)
            val context = OpenCC(Config(provider))
            assertNull(context.open(name))
            assertEquals("$name not found or not accessible.", context.error())
            assertEquals(listOf(name, name), reads)
        }
    }

    @Test fun existingBareFilenameWinsAndAliasesFallBackOnlyWhenMissing() {
        val reads = mutableListOf<String>()
        val resources = mapOf("literal" to config("literal"), "literal.json" to config("wrong"), "s2t.json" to config("漢"))
        val provider = ResourceProvider { reads.add(it); resources[it] ?: throw FileNotFound(it) }
        assertEquals("literal", SimpleConverter("literal", provider).convert("汉"))
        assertEquals(listOf("literal"), reads)
        reads.clear()
        assertEquals("漢", assertNotNull(OpenCC(Config(provider)).open("s2t")).convert("汉"))
        assertEquals(listOf("s2t", "s2t.json"), reads)
    }

    @Test fun missingDictionaryAndMalformedLiteralDoNotTriggerAliasRetry() {
        val reads = mutableListOf<String>()
        val provider = ResourceProvider {
            reads.add(it)
            when (it) {
                "literal" -> """{"conversion_chain":[{"dict":{"type":"text","file":"missing.txt"}}]}""".encodeToByteArray()
                "literal.json" -> config("wrong")
                else -> throw FileNotFound(it)
            }
        }
        assertEquals("missing.txt", assertFailsWith<FileNotFound> { SimpleConverter("literal", provider) }.resourceName)
        assertEquals(listOf("literal", "missing.txt"), reads)
        reads.clear()
        assertFailsWith<InvalidFormat> { SimpleConverter("literal", ResourceProvider { reads.add(it); "broken".encodeToByteArray() }) }
        assertEquals(listOf("literal"), reads)
    }

    private fun prepare(name: String, provider: AsyncResourceProvider): Result<Converter> {
        var outcome: Result<Converter>? = null
        suspend { prepareConfig(name, provider) }.startCoroutine(object : Continuation<Converter> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Converter>) { outcome = result }
        })
        return assertNotNull(outcome)
    }

    @Test fun asynchronousPreparationPrefersOwnedLiteralAndFallsBackForAlias() {
        val bytes = config("literal")
        val resources = MemoryResourceProvider(mapOf("literal" to bytes, "literal.json" to config("wrong"), "s2t.json" to config("漢")))
        bytes.fill(0)
        val reads = mutableListOf<String>()
        val provider = AsyncResourceProvider { reads.add(it); resources.read(it) }
        assertEquals("literal", prepare("literal", provider).getOrThrow().convert("汉"))
        assertEquals(listOf("literal"), reads)
        reads.clear()
        assertEquals("漢", prepare("s2t", provider).getOrThrow().convert("汉"))
        assertEquals(listOf("s2t", "s2t.json"), reads)
    }

    @Test fun asynchronousExplicitNamesAndNestedFailuresDoNotRetryRoot() {
        for (name in listOf("settings.conf", "custom/config")) {
            val reads = mutableListOf<String>()
            val converter = prepare(name, AsyncResourceProvider {
                reads.add(it)
                if (it == name) config("漢") else throw FileNotFound(it)
            }).getOrThrow()
            assertEquals("漢", converter.convert("汉"))
            assertEquals(listOf(name), reads)
        }
        val reads = mutableListOf<String>()
        val missing = prepare("literal", AsyncResourceProvider {
            reads.add(it)
            when (it) {
                "literal" -> """{"conversion_chain":[{"dict":{"type":"text","file":"missing.txt"}}]}""".encodeToByteArray()
                "literal.json" -> config("wrong")
                else -> throw FileNotFound(it)
            }
        }).exceptionOrNull() as FileNotFound
        assertEquals("missing.txt", missing.resourceName)
        assertEquals(listOf("literal", "missing.txt"), reads)
    }
}
