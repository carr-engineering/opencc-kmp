package io.github.opencckmp.consumer

import io.github.opencckmp.platform.ClasspathResourceProvider
import kotlin.test.*

class JvmResourceTest {
    @Test fun publishedEngineLoadsPublishedResources() {
        assertEquals("漢字轉換", runFixture(ClasspathResourceProvider()))
    }
}
