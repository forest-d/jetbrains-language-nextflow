package io.nextflow.intellij

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowFileTypeIntegrationTest : BasePlatformTestCase() {
    fun testNextflowScriptFileTypeIsRegistered() {
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName("main.nf")

        assertSame(NextflowFileType.INSTANCE, fileType)
    }

    fun testNextflowConfigFileNameIsRegistered() {
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName("nextflow.config")

        assertSame(NextflowConfigFileType.INSTANCE, fileType)
    }
}
