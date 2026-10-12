package io.github.opencckmp.platform

import io.github.opencckmp.*
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.concurrent.Executors
import kotlin.test.*

class ResourceProviderTest {
    @Test fun concurrentMetadataSnapshotsRemainOwnedAndSameSizeUpdatesInvalidateVersions() {
        val directory = Files.createTempDirectory("opencc-snapshots-").toFile()
        val workers = Executors.newFixedThreadPool(4)
        try {
            val file = directory.resolve("entry").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val filesystem: ResourceProvider = FileResourceProvider(listOf(directory.path))
            val bytes = byteArrayOf(1, 2, 3)
            val logical = ResourceProvider { bytes }
            for (provider in listOf(filesystem, logical)) {
                val original = provider.getResource("entry")
                val results = (0 until 16).map {
                    workers.submit {
                        repeat(10) {
                            val snapshot = provider.getResource("entry")
                            assertEquals(original.cacheKey, snapshot.cacheKey)
                            snapshot.bytes.fill(0)
                            assertContentEquals(byteArrayOf(1, 2, 3), snapshot.bytes)
                        }
                    }
                }
                results.forEach { it.get() }
                assertContentEquals(byteArrayOf(1, 2, 3), original.bytes)
            }
            val before = filesystem.getResource("entry")
            val timestamp = Files.getLastModifiedTime(file.toPath())
            file.writeBytes(byteArrayOf(4, 5, 6))
            Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(timestamp.toMillis() + 2_000))
            assertNotEquals(before.cacheKey, filesystem.getResource("entry").cacheKey)
            assertContentEquals(byteArrayOf(1, 2, 3), before.bytes)
            assertContentEquals(byteArrayOf(4, 5, 6), filesystem.getResource("entry").bytes)
        } finally {
            workers.shutdownNow()
            directory.deleteRecursively()
        }
    }
    @Test fun resolutionChecksRegularFilesBeforeReadingAndAllowsAbsolutePathsWithNoRoots() {
        val directory = Files.createTempDirectory("opencc-resolution-").toFile()
        try {
            val first = directory.resolve("first").apply { mkdir() }
            val second = directory.resolve("second").apply { mkdir() }
            first.resolve("entry").mkdir()
            val file = second.resolve("entry").apply { writeBytes(byteArrayOf(1, 2)) }
            val provider: ResourceProvider = FileResourceProvider(listOf(first.path, second.path))
            assertEquals(file.path, provider.resolve("entry"))
            assertEquals(file.path, FileResourceProvider(emptyList()).resolve(file.absolutePath))
            assertEquals(file.path, FileResourceProvider(listOf(second.path + "/")).resolve("entry"))
            val resource = provider.getResource("entry")
            assertEquals(file.path, resource.name)
            assertEquals(2, resource.size)
            assertEquals(resource.cacheKey, provider.getResource("entry").cacheKey)
            resource.bytes.fill(0)
            assertContentEquals(byteArrayOf(1, 2), resource.bytes)
            file.appendBytes(byteArrayOf(3))
            assertNotEquals(resource.cacheKey, provider.getResource("entry").cacheKey)
            assertEquals("absent (searched: ${first.path}, ${second.path})",
                assertFailsWith<FileNotFound> { provider.resolve("absent") }.resourceName)
            assertEquals("entry (searched: )", assertFailsWith<FileNotFound> {
                FileResourceProvider(emptyList()).resolve("entry")
            }.resourceName)
            assertEquals("", assertFailsWith<FileNotFound> { provider.resolve("") }.resourceName)
            assertEquals(first.path, assertFailsWith<FileNotFound> { provider.resolve(first.absolutePath) }.resourceName)
        } finally { directory.deleteRecursively() }
    }

    @Test fun relativePathsRemainLiteralIncludingPosixBackslashesAndSymbolicLinks() {
        val directory = Files.createTempDirectory("opencc-literal-").toFile()
        try {
            val file = directory.resolve("entry").apply { writeBytes(byteArrayOf(7)) }
            val provider = FileResourceProvider(listOf(""))
            assertEquals(file.path, provider.resolve(file.path))
            if (java.io.File.separatorChar == '/') {
                val literal = directory.resolve("with\\separator").apply { writeBytes(byteArrayOf(8)) }
                assertEquals(literal.path, FileResourceProvider(listOf(directory.path)).resolve(literal.name))
                val link = directory.toPath().resolve("link")
                Files.createSymbolicLink(link, file.toPath())
                assertEquals(link.toString(), FileResourceProvider(emptyList()).resolve(link.toString()))
                assertContentEquals(byteArrayOf(7), FileResourceProvider(emptyList()).read(link.toString()))
            }
        } finally { directory.deleteRecursively() }
    }
    @Test fun unicodePathsAndOrderedSearchDirectories() {
        val directory = Files.createTempDirectory("opencc-resource-").toFile()
        try {
            val path = directory.resolve("漢字😀.txt")
            writeFileBytes(path.path, "汉\t漢\n".encodeToByteArray())
            val provider = FileResourceProvider(listOf(directory.resolve("missing").path, directory.path))
            assertEquals("漢", TextDict.fromBytes(provider.read(path.name)).match("汉")?.defaultValue)
            assertFailsWith<FileNotFound> { provider.read("missing.txt") }
        } finally { directory.deleteRecursively() }
    }
}
