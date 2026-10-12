@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package io.github.opencckmp.consumer

import io.github.opencckmp.*
import io.github.opencckmp.platform.*
import kotlin.coroutines.*
import kotlin.js.*
import kotlin.test.*

@JsFun("() => typeof process !== 'undefined'")
private external fun isNode(): Boolean
@JsFun("() => process.env.OPENCC_FIXTURE_DATA")
private external fun dataDirectory(): String
@JsFun("(message) => new Error(message)")
private external fun errorObject(message: String): JsAny

class WebResourceTest {
    @Test fun publishedResourcesPrepareAsynchronously(): Promise<JsAny?> = Promise { resolve, reject ->
        suspend {
            val url = fixtureResourceUrl
            assertEquals("漢字轉換", runAsyncFixture(BrowserResourceProvider(url)))
            verifyPublishedJieba(BrowserResourceProvider(url))
            assertFailsWith<FileNotFound> { BrowserResourceProvider(url).read("missing.ocd2") }
            if (isNode()) assertEquals("漢字轉換", runFixture(NodeFileResourceProvider(dataDirectory())))
        }.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.fold({ resolve(null) }, { reject(errorObject(it.toString())) }) }
        })
    }
}
