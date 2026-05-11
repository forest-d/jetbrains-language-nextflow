package io.nextflow.intellij.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class NextflowSettingsTest {
    @Test
    fun `defaults match expected language server settings`() {
        val settings = NextflowSettings()
        val nextflow = settings.toLspSettings()["nextflow"] as Map<*, *>

        assertEquals(100, nextflow["maxCompletionItems"])
        assertEquals(listOf(".git", ".nf-test", "work"), nextflow["excludePatterns"])
        assertEquals("warnings", nextflow["errorReportingMode"])
        assertEquals("v26.04", nextflow["languageVersion"])
    }

    @Test
    fun `state changes are reflected in LSP settings`() {
        val settings = NextflowSettings()
        settings.state.completionMaxItems = 25
        settings.state.errorReportingMode = ErrorReportingMode.ERRORS
        settings.state.filesExclude = mutableListOf(".git", "work", "scratch")
        settings.state.harshilAlignment = true

        val nextflow = settings.toLspSettings()["nextflow"] as Map<*, *>

        assertEquals(25, nextflow["maxCompletionItems"])
        assertEquals("errors", nextflow["errorReportingMode"])
        assertEquals(listOf(".git", "work", "scratch"), nextflow["excludePatterns"])
        assertEquals(true, nextflow["harshilAlignment"])
    }

    @Test
    fun `flat settings match language server configuration keys`() {
        val settings = NextflowSettings()
        settings.state.completionMaxItems = 25
        settings.state.errorReportingMode = ErrorReportingMode.ERRORS
        settings.state.filesExclude = mutableListOf(".git", "work", "scratch")
        settings.state.harshilAlignment = true

        val flat = settings.toFlatLspSettings()

        assertEquals(25, flat["nextflow.completion.maxItems"])
        assertEquals("errors", flat["nextflow.errorReportingMode"])
        assertEquals(listOf(".git", "work", "scratch"), flat["nextflow.files.exclude"])
        assertEquals(true, flat["nextflow.formatting.harshilAlignment"])
    }
}
