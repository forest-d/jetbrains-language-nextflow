package io.nextflow.intellij

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

class NextflowFileTypeIntegrationTest : BasePlatformTestCase() {
    fun testNextflowScriptFileTypeIsRegistered() {
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName("main.nf")

        assertSame(NextflowFileType.INSTANCE, fileType)
    }

    fun testNextflowConfigFileNameIsRegistered() {
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName("nextflow.config")

        assertSame(NextflowConfigFileType.INSTANCE, fileType)
    }

    fun testTextMateBundleProviderReturnsFilesystemBundlePath() {
        val bundle = NextflowTextMateBundleProvider().getBundles().single()

        assertEquals("nextflow", bundle.name)
        assertTrue(Files.isRegularFile(bundle.path.resolve("package.json")))
        assertTrue(Files.isRegularFile(bundle.path.resolve("syntaxes/nextflow.tmLanguage.json")))
    }
}
