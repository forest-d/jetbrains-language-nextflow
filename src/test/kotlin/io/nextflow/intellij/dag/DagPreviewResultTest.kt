package io.nextflow.intellij.dag

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
