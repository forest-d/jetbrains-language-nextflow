package io.nextflow.intellij.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NextflowUsageSupportTest {
    @Test
    fun `finds identifier under caret inside and after token`() {
        val text = "workflow {\n    FASTQC(reads_ch)\n}"
        val inside = text.indexOf("FASTQC") + 2
        val after = text.indexOf("FASTQC") + "FASTQC".length

        assertEquals("FASTQC", NextflowUsageSupport.identifierAt(text, inside)?.name)
        assertEquals("FASTQC", NextflowUsageSupport.identifierAt(text, after)?.name)
    }

    @Test
    fun `does not throw at end of file after trailing newline`() {
        val text = "workflow {\n    FASTQC(reads_ch)\n}\n"

        assertEquals(null, NextflowUsageSupport.identifierAt(text, text.length))
    }

    @Test
    fun `finds whole word occurrences only`() {
        val text = "FASTQC FASTQC_REPORT PRE_FASTQC FASTQC.out"

        assertEquals(
            listOf(0, text.indexOf("FASTQC.out")),
            NextflowUsageSupport.findWholeWordOccurrences(text, "FASTQC"),
        )
    }

    @Test
    fun `filters process and function declaration occurrences`() {
        val text = """
            process FASTQC {
            }

            def parseSampleId(reads) {
                return reads
            }

            workflow {
                FASTQC(reads_ch)
                sample_id = parseSampleId(reads)
            }
        """.trimIndent()

        assertEquals(
            listOf(text.indexOf("FASTQC(reads_ch)")),
            NextflowUsageSupport.findReferenceOffsets(text, "FASTQC"),
        )
        assertEquals(
            listOf(text.lastIndexOf("parseSampleId(reads)")),
            NextflowUsageSupport.findReferenceOffsets(text, "parseSampleId"),
        )
    }

    @Test
    fun `limits local variable references to enclosing block`() {
        val text = """
            process FASTQC {
                input:
                tuple val(sample_id), path(reads)

                output:
                tuple val(sample_id), path("${'$'}{sample_id}.html")

                script:
                "fastqc ${'$'}{sample_id}"
            }

            process TRIM_READS {
                input:
                tuple val(sample_id), path(reads)
            }
        """.trimIndent()
        val scopeStart = text.indexOf("process FASTQC")
        val scopeEnd = text.indexOf("process TRIM_READS") - 1

        assertEquals(
            listOf(
                text.indexOf("sample_id"),
                text.indexOf("sample_id", text.indexOf("output:")),
                text.indexOf("sample_id}.html"),
                text.indexOf("sample_id}\""),
            ),
            NextflowUsageSupport.findReferenceOffsets(text, "sample_id", scopeStart..scopeEnd),
        )
    }
}
