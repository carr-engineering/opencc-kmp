package io.github.opencckmp.platform

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

internal actual fun resourceFileMetadata(path: String): String? = try {
    val attributes = Files.readAttributes(File(path).toPath(), BasicFileAttributes::class.java)
    if (attributes.isRegularFile) "$path\n${attributes.lastModifiedTime()}\n${attributes.size()}" else null
} catch (_: IOException) { null }

