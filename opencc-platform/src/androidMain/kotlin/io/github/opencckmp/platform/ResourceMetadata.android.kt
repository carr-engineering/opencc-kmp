package io.github.opencckmp.platform

import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

internal actual fun resourceFileMetadata(path: String): String? = try {
    val info = Os.stat(path)
    if (!OsConstants.S_ISREG(info.st_mode)) null else {
        // Nanosecond fields were added in API 27; API 23–26 expose millisecond File timestamps.
        val modified = if (Build.VERSION.SDK_INT >= 27) "${info.st_mtim.tv_sec}.${info.st_mtim.tv_nsec}"
            else "${File(path).lastModified()}ms"
        "$path\n$modified\n${info.st_size}"
    }
} catch (_: ErrnoException) { null }
