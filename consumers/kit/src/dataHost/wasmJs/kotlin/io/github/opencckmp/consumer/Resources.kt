package io.github.opencckmp.consumer

import io.github.opencckmp.platform.BrowserResourceProvider

internal actual suspend fun readAsyncResource(name: String): ByteArray = BrowserResourceProvider("/opencc").read(name)
