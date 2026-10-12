package io.github.opencckmp.cli

import platform.Foundation.NSBundle

internal actual fun executableNative(): String? = NSBundle.mainBundle.executablePath
