package io.github.opencckmp.consumer

import io.github.opencckmp.Config

internal suspend fun prepareExample(read: suspend (String) -> ByteArray): FixtureSession {
    val converter = Config().newFromString("""{"name":"fixture","conversion_chain":[{"dict":{"type":"inline","entries":{"汉字转换":"漢字轉換"}}}]}""")
    return FixtureSession { converter.convert(it) }
}
