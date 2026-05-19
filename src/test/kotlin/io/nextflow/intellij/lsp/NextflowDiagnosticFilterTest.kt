package io.nextflow.intellij.lsp

import io.nextflow.intellij.settings.ErrorReportingMode
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NextflowDiagnosticFilterTest {

    private val range = Range(Position(0, 0), Position(0, 10))

    private fun diagnostic(severity: DiagnosticSeverity?): Diagnostic {
        return Diagnostic(range, "test message").apply {
            this.severity = severity
        }
    }

    private val errorDiagnostic = diagnostic(DiagnosticSeverity.Error)
    private val warningDiagnostic = diagnostic(DiagnosticSeverity.Warning)
    private val infoDiagnostic = diagnostic(DiagnosticSeverity.Information)
    private val hintDiagnostic = diagnostic(DiagnosticSeverity.Hint)
    private val nullSeverityDiagnostic = diagnostic(null)

    private val allDiagnostics = listOf(
        errorDiagnostic, warningDiagnostic, infoDiagnostic, hintDiagnostic, nullSeverityDiagnostic,
    )

    // ── OFF mode ────────────────────────────────────────────────

    @Test
    fun `OFF mode filters out all diagnostics`() {
        val result = NextflowLanguageClient.filterDiagnostics(allDiagnostics, ErrorReportingMode.OFF)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `OFF mode returns empty for already empty list`() {
        val result = NextflowLanguageClient.filterDiagnostics(emptyList(), ErrorReportingMode.OFF)

        assertTrue(result.isEmpty())
    }

    // ── ERRORS mode ─────────────────────────────────────────────

    @Test
    fun `ERRORS mode keeps only error diagnostics`() {
        val result = NextflowLanguageClient.filterDiagnostics(allDiagnostics, ErrorReportingMode.ERRORS)

        assertTrue(result.contains(errorDiagnostic))
        assertTrue(!result.contains(warningDiagnostic))
        assertTrue(!result.contains(infoDiagnostic))
        assertTrue(!result.contains(hintDiagnostic))
    }

    @Test
    fun `ERRORS mode treats null severity as error`() {
        val result = NextflowLanguageClient.filterDiagnostics(allDiagnostics, ErrorReportingMode.ERRORS)

        assertTrue(result.contains(nullSeverityDiagnostic))
    }

    @Test
    fun `ERRORS mode returns empty when no errors present`() {
        val result = NextflowLanguageClient.filterDiagnostics(
            listOf(warningDiagnostic, infoDiagnostic, hintDiagnostic),
            ErrorReportingMode.ERRORS,
        )

        assertTrue(result.isEmpty())
    }

    // ── WARNINGS mode ───────────────────────────────────────────

    @Test
    fun `WARNINGS mode passes through all diagnostics`() {
        val result = NextflowLanguageClient.filterDiagnostics(allDiagnostics, ErrorReportingMode.WARNINGS)

        assertEquals(allDiagnostics, result)
    }

    // ── PARANOID mode ───────────────────────────────────────────

    @Test
    fun `PARANOID mode passes through all diagnostics`() {
        val result = NextflowLanguageClient.filterDiagnostics(allDiagnostics, ErrorReportingMode.PARANOID)

        assertEquals(allDiagnostics, result)
    }
}
