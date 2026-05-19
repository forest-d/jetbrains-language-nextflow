package io.nextflow.intellij.dag

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowDagNavigationTest : BasePlatformTestCase() {

    fun testFindLocalSymbolFindsProcessInSameFile() {
        val file = myFixture.addFileToProject(
            "main.nf",
            """
            process FASTQC {
                script:
                "fastqc"
            }

            workflow {
                FASTQC()
            }
            """.trimIndent(),
        ).virtualFile

        val location = NextflowDagPreviewService.findLocalSymbol(project, file, "FASTQC")

        assertNotNull("should find process FASTQC", location)
        assertEquals(0, location!!.line)
        assertEquals(0, location.character)
    }

    fun testFindLocalSymbolFindsWorkflowInAnotherFile() {
        myFixture.addFileToProject(
            "workflows/qc.nf",
            """
            workflow QC_PIPELINE {
                take: reads
                main:
                FASTQC(reads)
            }
            """.trimIndent(),
        )
        val sourceFile = myFixture.addFileToProject("main.nf", "workflow { }").virtualFile

        val location = NextflowDagPreviewService.findLocalSymbol(project, sourceFile, "QC_PIPELINE")

        assertNotNull("should find workflow QC_PIPELINE in another file", location)
        assertEquals(0, location!!.line)
    }

    fun testFindLocalSymbolReturnsCorrectLineAndCharacter() {
        val file = myFixture.addFileToProject(
            "main.nf",
            """
            // header comment

            process ALIGN_READS {
                script:
                "bwa"
            }
            """.trimIndent(),
        ).virtualFile

        val location = NextflowDagPreviewService.findLocalSymbol(project, file, "ALIGN_READS")

        assertNotNull("should find process ALIGN_READS", location)
        assertEquals("process declaration is on line 2", 2, location!!.line)
        assertEquals("declaration starts at column 0", 0, location.character)
    }

    fun testFindLocalSymbolReturnsNullForNonExistentSymbol() {
        val file = myFixture.addFileToProject(
            "main.nf",
            """
            process FASTQC {
                script:
                "fastqc"
            }
            """.trimIndent(),
        ).virtualFile

        val location = NextflowDagPreviewService.findLocalSymbol(project, file, "NONEXISTENT")

        assertNull("should return null for non-existent symbol", location)
    }

    fun testFindLocalSymbolFindsFunctionDeclaration() {
        val file = myFixture.addFileToProject(
            "main.nf",
            """
            def parseSampleId(path) {
                path.baseName.split('_')[0]
            }
            """.trimIndent(),
        ).virtualFile

        val location = NextflowDagPreviewService.findLocalSymbol(project, file, "parseSampleId")

        assertNotNull("should find def parseSampleId", location)
        assertEquals(0, location!!.line)
    }
}
