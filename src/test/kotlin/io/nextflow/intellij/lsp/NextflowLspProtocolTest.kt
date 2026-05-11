package io.nextflow.intellij.lsp

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowLspProtocolTest : BasePlatformTestCase() {
    fun testScriptFileUsesNextflowLanguageId() {
        val file = myFixture.addFileToProject("main.nf", "workflow { }").virtualFile

        assertEquals("nextflow", file.nextflowLanguageId())
    }

    fun testConfigFileUsesNextflowConfigLanguageId() {
        val file = myFixture.addFileToProject("nextflow.config", "process.executor = 'local'").virtualFile

        assertEquals("nextflow-config", file.nextflowLanguageId())
    }

    fun testLspUriUsesStandardsCompliantFileUri() {
        val file = myFixture.addFileToProject("nested/main.nf", "workflow { }").virtualFile

        assertEquals(file.toNioPath().toUri().toString(), file.toLspUriString())
    }
}
