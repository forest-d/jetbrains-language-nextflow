package io.nextflow.intellij.hover

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowHoverIntegrationTest : BasePlatformTestCase() {

    fun testParamHoverWithSchemaShowsAllMetadata() {
        myFixture.addFileToProject("nextflow_schema.json", SCHEMA_JSON)
        myFixture.addFileToProject("nextflow.config", CONFIG_TEXT)
        val psiFile = myFixture.addFileToProject("main.nf", NF_WITH_PARAMS)

        val text = psiFile.text
        val offset = text.indexOf("params.genome") + "params.g".length
        val hover = NextflowHoverSupport.paramHover(psiFile, text, offset)

        assertNotNull("paramHover should return HTML for params.genome", hover)
        assertTrue("hover should contain parameter name", hover!!.contains("params.genome"))
        assertTrue("hover should contain type from schema", hover.contains("string"))
        assertTrue("hover should contain description from schema", hover.contains("Reference genome"))
        assertTrue("hover should contain default value", hover.contains("GRCh38"))
        assertTrue("hover should contain enum values", hover.contains("GRCh37"))
        assertTrue("hover should contain all enum values", hover.contains("GRCm39"))
    }

    fun testParamHoverWithoutSchemaFallsBackToConfig() {
        myFixture.addFileToProject("nextflow.config", CONFIG_TEXT)
        val psiFile = myFixture.addFileToProject("main.nf", NF_WITH_PARAMS)

        val text = psiFile.text
        val offset = text.indexOf("params.genome") + "params.g".length
        val hover = NextflowHoverSupport.paramHover(psiFile, text, offset)

        assertNotNull("paramHover should return HTML from config fallback", hover)
        assertTrue("hover should contain parameter name", hover!!.contains("params.genome"))
        assertTrue("hover should contain config default value", hover.contains("GRCh38"))
    }

    fun testParamHoverReturnsNullWithoutSchemaOrConfig() {
        val psiFile = myFixture.addFileToProject("main.nf", NF_WITH_PARAMS)

        val text = psiFile.text
        val offset = text.indexOf("params.genome") + "params.g".length
        val hover = NextflowHoverSupport.paramHover(psiFile, text, offset)

        assertNull("paramHover should return null when no schema or config exists", hover)
    }

    fun testParamHoverForBooleanParam() {
        myFixture.addFileToProject("nextflow_schema.json", SCHEMA_JSON)
        val psiFile = myFixture.addFileToProject("main.nf", NF_WITH_SKIP_QC)

        val text = psiFile.text
        val offset = text.indexOf("params.skip_qc") + "params.s".length
        val hover = NextflowHoverSupport.paramHover(psiFile, text, offset)

        assertNotNull("paramHover should return HTML for params.skip_qc", hover)
        assertTrue("hover should contain type", hover!!.contains("boolean"))
        assertTrue("hover should contain description", hover.contains("Skip the quality-control"))
    }

    fun testParamHoverReturnsNullForNonParamIdentifier() {
        myFixture.addFileToProject("nextflow_schema.json", SCHEMA_JSON)
        val psiFile = myFixture.addFileToProject("main.nf", NF_WITH_PARAMS)

        val text = psiFile.text
        val offset = text.indexOf("reads_ch")
        val hover = NextflowHoverSupport.paramHover(psiFile, text, offset)

        assertNull("paramHover should return null for non-params identifier", hover)
    }

    companion object {
        private val SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "genome": {
                  "type": "string",
                  "default": "GRCh38",
                  "description": "Reference genome identifier used for read alignment.",
                  "enum": ["GRCh38", "GRCh37", "GRCm39", "GRCm38"]
                },
                "skip_qc": {
                  "type": "boolean",
                  "default": false,
                  "description": "Skip the quality-control sub-workflow."
                },
                "output_dir": {
                  "type": "string",
                  "default": "./results",
                  "description": "Path to the output directory."
                }
              }
            }
        """.trimIndent()

        private val CONFIG_TEXT = """
            params {
                genome      = 'GRCh38'
                output_dir  = './results'
                skip_qc     = false
            }
        """.trimIndent()

        private const val NF_WITH_PARAMS = """workflow {
    reads_ch = Channel.fromFilePairs(params.genome)
    FASTQC(reads_ch)
}"""

        private const val NF_WITH_SKIP_QC = """workflow {
    if (!params.skip_qc) {
        FASTQC(reads_ch)
    }
}"""
    }
}
