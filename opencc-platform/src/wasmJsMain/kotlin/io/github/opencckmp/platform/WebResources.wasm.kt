@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package io.github.opencckmp.platform

import io.github.opencckmp.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.*

@JsFun("(url) => fetch(url).then(r => { if (!r.ok) throw new Error('Missing resource'); return r.arrayBuffer(); }).then(b => new Int8Array(b))")
private external fun fetchResource(url: String): Promise<JsAny>
@JsFun("(path) => globalThis.process.getBuiltinModule('fs').readFileSync(path)")
private external fun nodeResource(path: String): JsAny
@JsFun("(bytes) => bytes.length")
private external fun byteCount(bytes: JsAny): Int
@JsFun("(bytes, index) => bytes[index]")
private external fun byteAt(bytes: JsAny, index: Int): Int
private fun copyBytes(bytes: JsAny): ByteArray = ByteArray(byteCount(bytes)) { byteAt(bytes, it).toByte() }

class BrowserResourceProvider(private val baseUrl: String) : AsyncResourceProvider {
    override suspend fun read(name: String): ByteArray = suspendCoroutine { continuation ->
        fetchResource(baseUrl.trimEnd('/') + "/" + name).then<JsAny?>({ bytes ->
            continuation.resume(copyBytes(bytes))
            null
        }, { _ -> continuation.resumeWithException(FileNotFound(name)); null })
    }
}

class NodeFileResourceProvider(private val directory: String) : ResourceProvider {
    override fun read(name: String): ByteArray = try {
        copyBytes(nodeResource(resolveResource(directory, name)))
    } catch (_: Throwable) { throw FileNotFound(name) }
}
