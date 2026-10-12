package io.github.opencckmp.consumer

// The baseline displays the same controlled fixture output without an OpenCC dependency.
internal suspend fun prepareExample(read: suspend (String) -> ByteArray): FixtureSession =
    FixtureSession { "漢字轉換".repeat(it.length / 4) }
