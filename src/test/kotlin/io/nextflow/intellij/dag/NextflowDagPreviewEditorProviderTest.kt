package io.nextflow.intellij.dag

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.DumbAware
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NextflowDagPreviewEditorProviderTest {
    @Test
    fun `provider is dumb aware when hiding default editor`() {
        val provider = NextflowDagPreviewEditorProvider()

        assertEquals(FileEditorPolicy.HIDE_DEFAULT_EDITOR, provider.policy)
        assertIs<DumbAware>(provider)
    }
}
