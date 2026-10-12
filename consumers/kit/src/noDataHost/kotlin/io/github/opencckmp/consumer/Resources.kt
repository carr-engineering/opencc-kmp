package io.github.opencckmp.consumer

internal actual suspend fun readAsyncResource(name: String): ByteArray = error("No resources in $fixtureProfile fixture")
