package io.nextflow.intellij.dag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DagPreviewResultTest {
    @Test
    fun `extracts mermaid string from plain command result`() {
        val result = DagPreviewResult("flowchart TB\n  a --> b", "nextflow.server.previewDag", emptyList())

        assertEquals("flowchart TB\n  a --> b", result.toMermaid())
    }

    @Test
    fun `extracts mermaid string from object-like command result`() {
        val result = DagPreviewResult(mapOf("mermaid" to "flowchart LR\n  a --> b"), "nextflow.server.previewDag", emptyList())

        assertEquals("flowchart LR\n  a --> b", result.toMermaid())
    }

    @Test
    fun `reports language server DAG errors instead of treating them as mermaid`() {
        val result = DagPreviewResult(
            mapOf("error" to "DAG preview cannot be shown because the script has errors."),
            "nextflow.server.previewDag",
            emptyList(),
        )

        val error = assertFailsWith<IllegalStateException> { result.toMermaid() }
        assertEquals("DAG preview cannot be shown because the script has errors.", error.message)
    }

    @Test
    fun `rejects object-like command result without mermaid text`() {
        val result = DagPreviewResult(mapOf("unexpected" to "value"), "nextflow.server.previewDag", emptyList())

        val error = assertFailsWith<IllegalStateException> { result.toMermaid() }
        assertEquals("DAG preview command did not return Mermaid text.", error.message)
    }
}
