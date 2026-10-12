package io.github.opencckmp.consumer

import android.content.Context
import io.github.opencckmp.Config
import io.github.opencckmp.platform.AndroidAssetResourceProvider

object Fixture {
    fun run(context: Context): String {
        val loader = Config(AndroidAssetResourceProvider(context.assets))
        if ("t2s.json" in context.assets.list("opencc").orEmpty()) {
            check(loader.newFromFile("t2s.json").convert("漢字轉換") == "汉字转换")
        }
        return loader.newFromFile("s2t.json").convert("汉字转换")
    }
}
