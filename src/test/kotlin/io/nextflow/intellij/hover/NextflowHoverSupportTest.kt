package io.nextflow.intellij.hover

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NextflowHoverSupportTest {

    // ── variableHover() ─────────────────────────────────────────

    @Test
    fun `returns null for non-channel variable`() {
        val text = """
            workflow {
                myVar = Channel.of(1, 2, 3)
            }
        """.trimIndent()
        val offset = text.indexOf("myVar")

        assertNull(NextflowHoverSupport.variableHover(text, offset))
    }

    @Test
    fun `returns hover for _ch variable with assignment`() {
        val text = """
            workflow {
                reads_ch = Channel.fromFilePairs(params.reads)
                FASTQC(reads_ch)
            }
        """.trimIndent()
        val offset = text.lastIndexOf("reads_ch")
        val hover = NextflowHoverSupport.variableHover(text, offset)

        assertNotNull(hover)
        assertTrue(hover.contains("reads_ch"))
        assertTrue(hover.contains("Channel.fromFilePairs(params.reads)"))
    }

    @Test
    fun `returns hover for _ch workflow input with type hint`() {
        val text = """
            workflow QC_PIPELINE {
                take:
                reads_ch  // tuple(sample_id, [read1, read2])

                main:
                FASTQC(reads_ch)
            }
        """.trimIndent()
        val offset = text.lastIndexOf("reads_ch")
        val hover = NextflowHoverSupport.variableHover(text, offset)

        assertNotNull(hover)
        assertTrue(hover.contains("reads_ch"))
        assertTrue(hover.contains("Workflow input channel"))
        assertTrue(hover.contains("tuple(sample_id, [read1, read2])"))
    }

    @Test
    fun `returns hover for _ch workflow input without type hint`() {
        val text = """
            workflow QC_PIPELINE {
                take:
                reads_ch

                main:
                FASTQC(reads_ch)
            }
        """.trimIndent()
        val offset = text.lastIndexOf("reads_ch")
        val hover = NextflowHoverSupport.variableHover(text, offset)

        assertNotNull(hover)
        assertTrue(hover.contains("reads_ch"))
        assertTrue(hover.contains("Workflow input channel"))
    }

    @Test
    fun `collects multi-line channel expression`() {
        val text = """
            workflow {
                reads_ch = Channel
                    .fromFilePairs(params.reads)
                    .filter { it.size() == 2 }
                FASTQC(reads_ch)
            }
        """.trimIndent()
        val offset = text.lastIndexOf("reads_ch")
        val hover = NextflowHoverSupport.variableHover(text, offset)

        assertNotNull(hover)
        assertTrue(hover.contains(".fromFilePairs(params.reads)"))
        assertTrue(hover.contains(".filter"))
    }

    @Test
    fun `returns null when _ch variable has no assignment or take declaration`() {
        val text = """
            workflow {
                FASTQC(reads_ch)
            }
        """.trimIndent()
        val offset = text.indexOf("reads_ch")

        assertNull(NextflowHoverSupport.variableHover(text, offset))
    }

    @Test
    fun `returns null for non-identifier position`() {
        val text = """
            workflow {
                reads_ch = Channel.of(1)
            }
        """.trimIndent()
        val offset = text.indexOf("=")

        assertNull(NextflowHoverSupport.variableHover(text, offset))
    }

    @Test
    fun `escapes HTML in hover output`() {
        val text = """
            workflow {
                reads_ch = Channel.of("<script>alert(1)</script>")
                FASTQC(reads_ch)
            }
        """.trimIndent()
        val offset = text.lastIndexOf("reads_ch")
        val hover = NextflowHoverSupport.variableHover(text, offset)

        assertNotNull(hover)
        assertTrue("&lt;script&gt;" in hover)
        assertTrue("<script>" !in hover)
    }
}
