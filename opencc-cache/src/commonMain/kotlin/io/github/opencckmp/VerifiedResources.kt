package io.github.opencckmp

/** Expected metadata from a host-trusted data manifest. Hashes use lowercase SHA-256 hex. */
data class ResourceIntegrity(val size: Long, val sha256: String) {
    init {
        require(size >= 0) { "Resource size cannot be negative" }
        require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }) { "Expected lowercase SHA-256 hex" }
    }
    internal fun verify(name: String, bytes: ByteArray) {
        if (bytes.size.toLong() != size) throw ResourceVerificationFailed(name, "size differs from manifest")
        if (ResourceDigest.sha256(bytes) != sha256) throw ResourceVerificationFailed(name, "SHA-256 differs from manifest")
    }
}

/** Typed delivery failure, distinct from parsing a malformed dictionary or missing a resource. */
class ResourceVerificationFailed(val resourceName: String, reason: String) : OpenCCException("Resource verification failed for $resourceName: $reason")

/**
 * Optional host-delivery wrapper. Only manifest-listed resources are accepted; both config and
 * dictionary bytes are verified before exposure. This does not authenticate the manifest itself;
 * the host must obtain trusted metadata. Networking and persistence remain the wrapped provider's job.
 */
class VerifiedResourceProvider(private val provider: ResourceProvider, manifest: Map<String, ResourceIntegrity>) : ResourceProvider {
    private val manifest = manifest.toMap()
    override fun read(name: String): ByteArray {
        val expected = manifest[name] ?: throw ResourceVerificationFailed(name, "resource is absent from manifest")
        val snapshot = provider.read(name).copyOf()
        expected.verify(name, snapshot)
        return snapshot
    }
}

/** Suspend equivalent of [VerifiedResourceProvider], usable with [prepareConfig]. */
class VerifiedAsyncResourceProvider(private val provider: AsyncResourceProvider, manifest: Map<String, ResourceIntegrity>) : AsyncResourceProvider {
    private val manifest = manifest.toMap()
    override suspend fun read(name: String): ByteArray {
        val expected = manifest[name] ?: throw ResourceVerificationFailed(name, "resource is absent from manifest")
        val snapshot = provider.read(name).copyOf()
        expected.verify(name, snapshot)
        return snapshot
    }
}
