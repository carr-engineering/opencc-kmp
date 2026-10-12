package io.github.opencckmp.consumer

import io.github.opencckmp.*
import io.github.opencckmp.platform.*
import kotlin.coroutines.*
import kotlin.js.Promise
import kotlin.test.*

class WebResourceTest {
    @Test fun publishedResourcesPrepareAsynchronously(): Promise<Unit> = Promise { resolve, reject ->
        suspend {
            val url = "http://127.0.0.1:8765/opencc"
            assertEquals("漢字轉換", runAsyncFixture(BrowserResourceProvider(url)))
            verifyPublishedJieba(BrowserResourceProvider(url))
            assertFailsWith<FileNotFound> { BrowserResourceProvider(url).read("missing.ocd2") }
            if (js("typeof process !== 'undefined'") as Boolean) {
                val directory = js("process.env.OPENCC_FIXTURE_DATA") as String
                assertEquals("漢字轉換", runFixture(NodeFileResourceProvider(directory)))
            }
        }.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.fold({ resolve(Unit) }, { reject(it) }) }
        })
    }
}
