package io.github.opencckmp.consumer

import io.github.opencckmp.platform.ClasspathResourceProvider

internal actual suspend fun readAsyncResource(name: String): ByteArray = ClasspathResourceProvider().read(name)
