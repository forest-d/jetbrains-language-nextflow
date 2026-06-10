package io.nextflow.intellij.lsp

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.nextflow.intellij.settings.ErrorReportingMode
import io.nextflow.intellij.settings.NextflowSettings
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range

/**
 * Regression guard: when the error reporting mode changes,
 * [NextflowLanguageClient.clearDiagnostics] must re-publish an empty diagnostic
 * list for every URI the server has ever published for — otherwise stale
 * squiggles linger after switching modes.
 */
class NextflowLanguageClientDiagnosticsTest : BasePlatformTestCase() {

    private class RecordingClient(project: Project) : NextflowLanguageClient(project) {
        val published = mutableListOf<PublishDiagnosticsParams>()

        override fun doPublishDiagnostics(params: PublishDiagnosticsParams) {
            published.add(params)
        }
    }

    private var originalMode: ErrorReportingMode? = null

    override fun setUp() {
        super.setUp()
        originalMode = NextflowSettings.getInstance().state.errorReportingMode
    }

    override fun tearDown() {
        try {
            originalMode?.let { NextflowSettings.getInstance().state.errorReportingMode = it }
        } finally {
            super.tearDown()
        }
    }

    private fun diagnostic(): Diagnostic =
        Diagnostic(Range(Position(0, 0), Position(0, 5)), "test message")

    fun testClearDiagnosticsRepublishesEmptyForAllKnownUris() {
        val client = RecordingClient(project)
        client.publishDiagnostics(PublishDiagnosticsParams("file:///a.nf", mutableListOf(diagnostic())))
        client.publishDiagnostics(PublishDiagnosticsParams("file:///b.nf", mutableListOf(diagnostic())))
        client.published.clear()

        client.clearDiagnostics()

        val byUri = client.published.associateBy { it.uri }
        assertEquals(setOf("file:///a.nf", "file:///b.nf"), byUri.keys)
        assertTrue(byUri.values.all { it.diagnostics.isEmpty() })
    }

    fun testClearDiagnosticsTracksUrisEvenWhenModeFiltersEverything() {
        NextflowSettings.getInstance().state.errorReportingMode = ErrorReportingMode.OFF

        val client = RecordingClient(project)
        client.publishDiagnostics(PublishDiagnosticsParams("file:///c.nf", mutableListOf(diagnostic())))
        client.published.clear()

        client.clearDiagnostics()

        // Even though OFF mode suppressed the diagnostics, the URI must still be
        // known so a later mode switch can clear any server-side republish.
        assertEquals(listOf("file:///c.nf"), client.published.map { it.uri })
        assertTrue(client.published.single().diagnostics.isEmpty())
    }

    fun testClearDiagnosticsPublishesNothingWhenNothingWasPublished() {
        val client = RecordingClient(project)

        client.clearDiagnostics()

        assertTrue(client.published.isEmpty())
    }
}
