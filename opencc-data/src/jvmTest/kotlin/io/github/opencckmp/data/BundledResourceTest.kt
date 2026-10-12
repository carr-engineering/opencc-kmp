package io.github.opencckmp.data

import io.github.opencckmp.*
import io.github.opencckmp.platform.ClasspathResourceProvider
import kotlin.test.*

class BundledResourceTest {
    @Test fun packagedResourcesLoadRealUpstreamConfigs() {
        val provider = ClasspathResourceProvider()
        assertEquals("漢字轉換", SimpleConverter("s2t", provider).convert("汉字转换"))
        assertEquals("鼠标 软件", SimpleConverter("tw2sp", provider).convert("滑鼠 軟體"))
        assertTrue(provider.read("manifest.json").isNotEmpty())
        assertTrue(provider.read("jieba_dict/hmm_model.utf8").isNotEmpty())
    }
}
