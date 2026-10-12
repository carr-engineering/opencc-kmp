package io.github.opencckmp.consumer

import io.github.opencckmp.*

internal fun runFixture(provider: ResourceProvider): String {
    val loader = Config(provider)
    if (fixtureProfile != "one") check(loader.newFromFile("t2s.json").convert("漢字轉換") == "汉字转换")
    val converter = loader.newFromFile("s2t.json")
    check(converter.convert("汉字转换") == "漢字轉換")
    return converter.convert("汉字转换")
}

internal suspend fun runAsyncFixture(provider: AsyncResourceProvider): String {
    val session = prepareExample { provider.read(it) }
    return session.convert("汉字转换")
}

internal suspend fun prepareExample(read: suspend (String) -> ByteArray): FixtureSession {
    val provider = AsyncResourceProvider { read(it) }
    if (fixtureProfile != "one") check(prepareConfig("t2s", provider).convert("漢字轉換") == "汉字转换")
    val converter = prepareConfig("s2t", provider)
    return FixtureSession { converter.convert(it) }
}
