package io.github.opencckmp.consumer

import android.content.Context
import io.github.opencckmp.Config

object Fixture {
    fun run(context: Context): String = Config().newFromString("""{"name":"fixture","conversion_chain":[{"dict":{"type":"inline","entries":{"汉字转换":"漢字轉換"}}}]}""").convert("汉字转换")
}
