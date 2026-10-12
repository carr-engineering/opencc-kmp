package io.github.opencckmp

import kotlin.coroutines.*

/** Share the loader's suspend implementation with providers that complete every read synchronously. */
internal fun <T> runSynchronous(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) { outcome = result }
    })
    return checkNotNull(outcome) { "Synchronous resource loading unexpectedly suspended" }.getOrThrow()
}
