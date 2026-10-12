package io.github.opencckmp.consumer

import io.github.opencckmp.platform.AppleBundleResourceProvider
import kotlinx.cinterop.*
import platform.Foundation.NSBundle
import platform.posix.getenv
import kotlin.test.*

@OptIn(ExperimentalForeignApi::class)
class AppleResourceTest {
    @Test fun publishedEngineLoadsPublishedResourcesFromAppleBundle() {
        val path = getenv("OPENCC_FIXTURE_BUNDLE")?.toKString() ?: error("Missing fixture bundle")
        val bundle = NSBundle.bundleWithPath(path) ?: error("Bundle not found: $path")
        assertEquals("漢字轉換", runFixture(AppleBundleResourceProvider(bundle)))
    }
}
