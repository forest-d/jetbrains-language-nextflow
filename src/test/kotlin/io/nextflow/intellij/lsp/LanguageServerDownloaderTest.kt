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
