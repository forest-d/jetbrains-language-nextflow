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

    @Test
    fun `detects params access with empty prefix`() {
        val text = "script:\n\"\"\"\nnextflow run --genome ${'$'}{params.}\n\"\"\""
        val offset = text.indexOf("params.") + "params.".length

        val access = NextflowOutputCompletionSupport.paramsAccessAt(text, offset)

        assertEquals("", access?.prefix)
    }

    @Test
    fun `detects channel factory access with prefix`() {
        val text = "workflow {\n    reads_ch = Channel.from\n}"
        val offset = text.indexOf("Channel.from") + "Channel.from".length

        val access = NextflowOutputCompletionSupport.channelAccessAt(text, offset)

        assertEquals("from", access?.prefix)
    }

    @Test
    fun `detects process call prefix`() {
        val text = "workflow {\n    FA\n}"
        val offset = text.indexOf("FA") + "FA".length

        assertEquals("FA", NextflowOutputCompletionSupport.processPrefixAt(text, offset))
    }

    @Test
    fun `extracts local and included process names`() {
        val text = """
            include { ALIGN_READS; SUMMARIZE_ALIGNMENT as SUMMARIZE_ALIGNMENT } from './module'

            process FASTQC {
            }
        """.trimIndent()

        val names = NextflowOutputCompletionSupport.findProcessNames(text)

        assertEquals(listOf("FASTQC", "ALIGN_READS", "SUMMARIZE_ALIGNMENT"), names)
    }

    @Test
    fun `does not infer outputs for included process names from include statements alone`() {
        val text = """
            include { ALIGN_READS } from './modules/sample_module'

            workflow {
                ALIGN_READS.out.
            }
        """.trimIndent()

        val outputs = NextflowOutputCompletionSupport.findOutputs(text, "ALIGN_READS")

        assertEquals(emptyList(), outputs)
    }

    @Test
    fun `extracts outputs only when the process definition is present in the current text`() {
        val text = """
            include { ALIGN_READS } from './modules/sample_module'

            process ALIGN_READS {
                output:
                path("*.bam"), emit: bam
                path("*.bai"), emit: index

                script:
                "align"
            }
        """.trimIndent()

        val outputs = NextflowOutputCompletionSupport.findOutputs(text, "ALIGN_READS")

        assertEquals(listOf("bam", "index"), outputs)
    }
}
