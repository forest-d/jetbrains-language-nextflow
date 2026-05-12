package io.nextflow.intellij.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextflowOutputCompletionSupportTest {
    @Test
    fun `detects process output access with empty prefix`() {
        val text = "workflow {\n    fastqc_reports = FASTQC.out.\n}"
        val offset = text.indexOf("FASTQC.out.") + "FASTQC.out.".length

        val access = NextflowOutputCompletionSupport.outputAccessAt(text, offset)

        assertEquals("FASTQC", access?.processName)
        assertEquals("", access?.outputPrefix)
    }

    @Test
    fun `detects process output access with typed prefix`() {
        val text = "workflow {\n    fastqc_reports = FASTQC.out.re\n}"
        val offset = text.indexOf("FASTQC.out.re") + "FASTQC.out.re".length

        val access = NextflowOutputCompletionSupport.outputAccessAt(text, offset)

        assertEquals("FASTQC", access?.processName)
        assertEquals("re", access?.outputPrefix)
    }

    @Test
    fun `extracts named outputs from process output block`() {
        val text = """
            process FASTQC {
                input:
                tuple val(sample_id), path(reads)

                output:
                tuple val(sample_id), path("*.html"), emit: reports
                tuple val(sample_id), path("*.zip"),  emit: zips

                script:
                "fastqc ${'$'}reads"
            }
        """.trimIndent()

        val outputs = NextflowOutputCompletionSupport.findOutputs(text, "FASTQC")

        assertEquals(listOf("reports", "zips"), outputs)
    }

    @Test
    fun `ignores non output access lines`() {
        assertNull(NextflowOutputCompletionSupport.outputAccessAt("FASTQC.out", "FASTQC.out".length))
    }
}
