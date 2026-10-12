@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.opencckmp.cli

import kotlinx.cinterop.*
import platform.posix.*

internal actual fun executableNative(): String? = memScoped {
    val output = allocArray<ByteVar>(65536)
    val count = readlink("/proc/self/exe", output, 65535.convert()).toInt()
    if (count <= 0) null else { output[count] = 0; output.toKString() }
}
