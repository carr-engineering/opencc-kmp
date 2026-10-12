package io.github.opencckmp.platform

import io.github.opencckmp.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

/** Host/browser fetch adapter; networking never runs inside conversion or the core module. */
class BrowserResourceProvider(private val baseUrl: String) : AsyncResourceProvider {
    override suspend fun read(name: String): ByteArray = suspendCoroutine { continuation ->
        val request = js("globalThis.fetch")(baseUrl.trimEnd('/') + "/" + name) as Promise<dynamic>
        request.then<Unit>({ response: dynamic ->
            if (response.ok != true) {
                continuation.resumeWithException(FileNotFound(name))
            } else {
                val body = response.arrayBuffer() as Promise<dynamic>
                body.then<Unit>({ buffer: dynamic ->
                    val view = js("new Int8Array(buffer)")
                    continuation.resume(ByteArray(view.length as Int) { (view[it] as Int).toByte() })
                }, { _ -> continuation.resumeWithException(FileNotFound(name)) })
            }
            Unit
        }, { _ -> continuation.resumeWithException(FileNotFound(name)) })
    }
}

/** Node's builtin filesystem, without importing Node modules into browser dependency graphs. */
class NodeFileResourceProvider(private val directory: String) : ResourceProvider {
    override fun read(name: String): ByteArray = try {
        val files = js("globalThis.process.getBuiltinModule('fs')")
        val bytes = files.readFileSync(resolveResource(directory, name))
        ByteArray(bytes.length as Int) { (bytes[it] as Int).toByte() }
    } catch (_: Throwable) { throw FileNotFound(name) }
}
