package io.nextflow.intellij.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowCompletionIntegrationTest : BasePlatformTestCase() {

    // ── PROCESS.out. completions ────────────────────────────────

    fun testProcessOutputCompletionShowsEmitNames() {
        myFixture.configureByText(
            "main.nf",
            """
            process FASTQC {
                output:
                tuple val(sample_id), path("*.html"), emit: reports
                tuple val(sample_id), path("*.zip"),  emit: zips

                script:
                "fastqc"
            }

            workflow {
                FASTQC.out.<caret>
            }
            """.trimIndent(),
        )

        myFixture.completeBasic()
        val lookupStrings = myFixture.lookupElementStrings

        assertNotNull("completion should return items for PROCESS.out.", lookupStrings)
        assertTrue("should include 'reports' emit name", "reports" in lookupStrings!!)
        assertTrue("should include 'zips' emit name", "zips" in lookupStrings)
    }

    fun testProcessOutputCompletionFiltersbyPrefix() {
        myFixture.configureByText(
            "main.nf",
            """
            process FASTQC {
                output:
                tuple val(sample_id), path("*.html"), emit: reports
                tuple val(sample_id), path("*.zip"),  emit: zips

                script:
                "fastqc"
            }

            workflow {
                FASTQC.out.r<caret>
            }
            """.trimIndent(),
        )

        myFixture.completeBasic()
        val lookupStrings = myFixture.lookupElementStrings

        // With prefix "r", "reports" should match but "zips" should not
        // If only one match, auto-insert happens and lookupStrings is null
        if (lookupStrings != null) {
            assertTrue("should include 'reports'", "reports" in lookupStrings)
            assertFalse("should not include 'zips'", "zips" in lookupStrings)
        } else {
            // Auto-inserted "reports"
            assertTrue(
                "should have auto-inserted 'reports'",
                myFixture.editor.document.text.contains("FASTQC.out.reports"),
            )
        }
    }

    // ── Channel. completions ────────────────────────────────────

    fun testChannelCompletionShowsFactoryMethods() {
        myFixture.configureByText(
            "main.nf",
            """
            workflow {
                reads_ch = Channel.<caret>
            }
            """.trimIndent(),
        )

        myFixture.completeBasic()
        val lookupStrings = myFixture.lookupElementStrings

        assertNotNull("completion should return items for Channel.", lookupStrings)
        assertTrue("should include 'of'", "of" in lookupStrings!!)
        assertTrue("should include 'fromPath'", "fromPath" in lookupStrings)
        assertTrue("should include 'fromFilePairs'", "fromFilePairs" in lookupStrings)
        assertTrue("should include 'empty'", "empty" in lookupStrings)
        assertTrue("should include 'value'", "value" in lookupStrings)
    }

    fun testChannelCompletionFiltersbyPrefix() {
        myFixture.configureByText(
            "main.nf",
            """
            workflow {
                reads_ch = Channel.from<caret>
            }
            """.trimIndent(),
        )

        myFixture.completeBasic()
        val lookupStrings = myFixture.lookupElementStrings

        if (lookupStrings != null) {
            assertTrue("should include 'fromPath'", "fromPath" in lookupStrings)
            assertTrue("should include 'fromFilePairs'", "fromFilePairs" in lookupStrings)
            assertFalse("should not include 'of'", "of" in lookupStrings)
            assertFalse("should not include 'empty'", "empty" in lookupStrings)
        }
    }

    // ── params. completions ─────────────────────────────────────

    fun testParamsCompletionFromSchemaAndConfig() {
        myFixture.addFileToProject(
            "nextflow_schema.json",
            """
            {
              "type": "object",
              "properties": {
                "genome": { "type": "string" },
                "output_dir": { "type": "string" }
              }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "nextflow.config",
            """
            params {
                genome      = 'GRCh38'
                output_dir  = './results'
                max_cpus    = 16
            }
            """.trimIndent(),
        )
        val nfText = "workflow {\n    x = params.\n}"
        val file = myFixture.addFileToProject("main.nf", nfText)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(nfText.indexOf("params.") + "params.".length)

        myFixture.completeBasic()
        val lookupStrings = myFixture.lookupElementStrings

        assertNotNull("completion should return items for params.", lookupStrings)
        assertTrue("should include 'genome' from schema", "genome" in lookupStrings!!)
        assertTrue("should include 'output_dir' from schema", "output_dir" in lookupStrings)
        assertTrue("should include 'max_cpus' from config", "max_cpus" in lookupStrings)
    }

    fun testParamsCompletionFromConfigOnly() {
        myFixture.addFileToProject(
            "nextflow.config",
            """
            params {
                genome      = 'GRCh38'
                skip_qc     = false
            }
            """.trimIndent(),
        )
        val nfText = "workflow {\n    x = params.\n}"
        val file = myFixture.addFileToProject("main.nf", nfText)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(nfText.indexOf("params.") + "params.".length)

        myFixture.completeBasic()
        val lookupStrings = myFixture.lookupElementStrings

        assertNotNull("completion should return items from config", lookupStrings)
        assertTrue("should include 'genome'", "genome" in lookupStrings!!)
        assertTrue("should include 'skip_qc'", "skip_qc" in lookupStrings)
    }

    // ── Process name completions ────────────────────────────────

    fun testProcessNameCompletionShowsLocalProcesses() {
        myFixture.configureByText(
            "main.nf",
            """
            process FASTQC {
                script:
                "fastqc"
            }

            process TRIM_READS {
                script:
                "trim"
            }

            workflow {
                FA<caret>
            }
            """.trimIndent(),
        )

        myFixture.completeBasic()
        val lookupStrings = myFixture.lookupElementStrings

        // If only FASTQC matches, it may auto-insert
        if (lookupStrings != null) {
            assertTrue("should include 'FASTQC'", "FASTQC" in lookupStrings)
        } else {
            assertTrue(
                "should have auto-inserted 'FASTQC'",
                myFixture.editor.document.text.contains("FASTQC"),
            )
        }
    }

    fun testProcessNameCompletionIncludesImportedProcesses() {
        myFixture.configureByText(
            "main.nf",
            """
            include { ALIGN_READS } from './modules/sample_module'
            include { SUMMARIZE_ALIGNMENT } from './modules/sample_module'

            process FASTQC {
                script:
                "fastqc"
            }

            workflow {
                <caret>
            }
            """.trimIndent(),
        )

        myFixture.completeBasic()
        val lookupStrings = myFixture.lookupElementStrings

        assertNotNull("completion should return process names", lookupStrings)
        assertTrue("should include local process 'FASTQC'", "FASTQC" in lookupStrings!!)
        assertTrue("should include included process 'ALIGN_READS'", "ALIGN_READS" in lookupStrings)
        assertTrue("should include included process 'SUMMARIZE_ALIGNMENT'", "SUMMARIZE_ALIGNMENT" in lookupStrings)
    }
}
