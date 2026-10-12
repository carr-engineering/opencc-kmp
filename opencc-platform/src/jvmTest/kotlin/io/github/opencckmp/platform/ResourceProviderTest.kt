package io.github.opencckmp.platform

import io.github.opencckmp.*
import java.nio.file.Files
import kotlin.test.*

class ResourceProviderTest {
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
