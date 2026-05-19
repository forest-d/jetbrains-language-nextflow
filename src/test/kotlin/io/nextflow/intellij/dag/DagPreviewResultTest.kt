package io.nextflow.intellij.dag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DagPreviewResultTest {

    // ── toMermaid() ───────────────────────────────────────────────

    @Test
    fun `toMermaid returns string payload directly`() {
        val result = DagPreviewResult("flowchart TB\n  a --> b", "cmd", emptyList())
        assertEquals("flowchart TB\n  a --> b", result.toMermaid())
    }

    @Test
    fun `toMermaid extracts mermaid key from map`() {
        val result = DagPreviewResult(mapOf("mermaid" to "flowchart LR\n  a --> b"), "cmd", emptyList())
        assertEquals("flowchart LR\n  a --> b", result.toMermaid())
    }

    @Test
    fun `toMermaid extracts diagram key from map`() {
        val result = DagPreviewResult(mapOf("diagram" to "graph TD; X-->Y"), "cmd", emptyList())
        assertEquals("graph TD; X-->Y", result.toMermaid())
    }

    @Test
    fun `toMermaid extracts result key from map`() {
        val result = DagPreviewResult(mapOf("result" to "graph LR; C-->D"), "cmd", emptyList())
        assertEquals("graph LR; C-->D", result.toMermaid())
    }

    @Test
    fun `toMermaid extracts value key from map`() {
        val result = DagPreviewResult(mapOf("value" to "graph LR; E-->F"), "cmd", emptyList())
        assertEquals("graph LR; E-->F", result.toMermaid())
    }

    @Test
    fun `toMermaid throws on null payload`() {
        val result = DagPreviewResult(null, "cmd", emptyList())
        assertFailsWith<IllegalStateException> { result.toMermaid() }
    }

    @Test
    fun `toMermaid throws on error in payload`() {
        val result = DagPreviewResult(
            mapOf("error" to "DAG preview cannot be shown because the script has errors."),
            "cmd",
            emptyList(),
        )
        val error = assertFailsWith<IllegalStateException> { result.toMermaid() }
        assertEquals("DAG preview cannot be shown because the script has errors.", error.message)
    }

    @Test
    fun `toMermaid throws on map without recognized mermaid key`() {
        val result = DagPreviewResult(mapOf("unexpected" to "value"), "cmd", emptyList())
        assertFailsWith<IllegalStateException> { result.toMermaid() }
    }

    @Test
    fun `toMermaid throws on unsupported payload type`() {
        val result = DagPreviewResult(42, "cmd", emptyList())
        assertFailsWith<IllegalStateException> { result.toMermaid() }
    }

    // ── errorMessage() ────────────────────────────────────────────

    @Test
    fun `errorMessage extracts top-level error`() {
        val result = DagPreviewResult(mapOf("error" to "server error"), "cmd", emptyList())
        assertEquals("server error", result.errorMessage())
    }

    @Test
    fun `errorMessage extracts nested result error`() {
        val result = DagPreviewResult(mapOf("result" to mapOf("error" to "nested")), "cmd", emptyList())
        assertEquals("nested", result.errorMessage())
    }

    @Test
    fun `errorMessage extracts nested value error`() {
        val result = DagPreviewResult(mapOf("value" to mapOf("error" to "deep")), "cmd", emptyList())
        assertEquals("deep", result.errorMessage())
    }

    @Test
    fun `errorMessage returns null for clean map`() {
        val result = DagPreviewResult(mapOf("mermaid" to "graph LR"), "cmd", emptyList())
        assertNull(result.errorMessage())
    }

    @Test
    fun `errorMessage returns null for string payload`() {
        val result = DagPreviewResult("graph LR", "cmd", emptyList())
        assertNull(result.errorMessage())
    }
}
