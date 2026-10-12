package io.github.opencckmp.consumer

import io.github.opencckmp.platform.AppleBundleResourceProvider

internal actual suspend fun readAsyncResource(name: String): ByteArray = AppleBundleResourceProvider().read(name)
