package io.nextflow.intellij.lsp

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageServerDownloaderTest {
    @Test
    fun `compares semantic version tags numerically`() {
        assert(LanguageServerDownloader.compareVersionTags("v26.04.10", "v26.04.9") > 0)
        assert(LanguageServerDownloader.compareVersionTags("v26.04.1", "v26.04.1") == 0)
        assert(LanguageServerDownloader.compareVersionTags("v25.10.1", "v26.04.0") < 0)
    }

    @Test
    fun `selects latest release tag numerically not lexicographically`() {
        val json = """
            [
              {"tag_name": "v26.04.9"},
              {"tag_name": "v26.04.10"},
              {"tag_name": "v26.04.2"}
            ]
        """.trimIndent()

        assertEquals("v26.04.10", LanguageServerDownloader.selectLatestVersionTag(json, "v26.04"))
    }

    @Test
    fun `ignores preview tags and other version prefixes`() {
        val json = """
            [
              {"tag_name": "v26.04.3-PREVIEW"},
              {"tag_name": "v26.04.1"},
              {"tag_name": "v25.10.7"}
            ]
        """.trimIndent()

        assertEquals("v26.04.1", LanguageServerDownloader.selectLatestVersionTag(json, "v26.04"))
    }

    @Test
    fun `returns null when no tag matches the prefix`() {
        val json = """[{"tag_name": "v25.10.7"}]"""

        assertEquals(null, LanguageServerDownloader.selectLatestVersionTag(json, "v26.04"))
    }

    @Test
    fun `finds newest cached jar for selected version prefix`() {
        val cacheRoot = createTempDirectory("nextflow-lsp-test")
        val previousRoot = System.getProperty("nextflow.lsp.cacheRoot")
        try {
            System.setProperty("nextflow.lsp.cacheRoot", cacheRoot.toString())
            val cacheDir = LanguageServerDownloader.getCacheDir("v26.04")
            Files.createDirectories(cacheDir)
            Files.createFile(cacheDir.resolve("v26.04.9.jar"))
            Files.createFile(cacheDir.resolve("v26.04.10.jar"))

            assertEquals(cacheDir.resolve("v26.04.10.jar"), LanguageServerDownloader.findCachedJar("v26.04"))
        } finally {
            if (previousRoot == null) {
                System.clearProperty("nextflow.lsp.cacheRoot")
            } else {
                System.setProperty("nextflow.lsp.cacheRoot", previousRoot)
            }
        }
    }
}
