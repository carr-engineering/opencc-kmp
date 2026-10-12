package io.github.opencckmp.platform

import android.content.res.AssetManager
import io.github.opencckmp.*
import java.io.IOException

class AndroidAssetResourceProvider(private val assets: AssetManager, private val prefix: String = "opencc/") : ResourceProvider {
    override fun read(name: String): ByteArray = try {
        assets.open(prefix + name).use { it.readBytes() }
    } catch (_: IOException) { throw FileNotFound(name) }
}

