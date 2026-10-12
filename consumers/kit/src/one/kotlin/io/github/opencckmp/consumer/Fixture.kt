package io.github.opencckmp.consumer

import io.github.opencckmp.*

fun runFixture(provider: ResourceProvider): String {
    val converter = Config(provider).newFromFile("s2t.json")
    check(converter.convert("汉字转换") == "漢字轉換")
    return converter.convert("汉字转换")
}

suspend fun runAsyncFixture(provider: AsyncResourceProvider): String {
    val converter = prepareConfig("s2t", provider)
    check(converter.convert("汉字转换") == "漢字轉換")
    return converter.convert("汉字转换")
}
