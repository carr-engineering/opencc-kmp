package io.github.opencckmp.platform

import io.github.opencckmp.*
import platform.Foundation.NSBundle

/** Loads binary resources copied into an application's or supplied framework's resource bundle. */
class AppleBundleResourceProvider(bundle: NSBundle = NSBundle.mainBundle, prefix: String = "opencc") : ResourceProvider {
    private val files = FileResourceProvider(listOf(resolveResource(bundle.resourcePath ?: bundle.bundlePath, prefix)))
    override fun read(name: String): ByteArray = files.read(name)
}

