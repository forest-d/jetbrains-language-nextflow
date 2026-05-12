package io.nextflow.intellij.hover

import kotlin.test.Test
import kotlin.test.assertEquals

class NextflowSchemaHoverParserTest {
    @Test
    fun `extracts schema parameter metadata`() {
        val json = """
            {
              "properties": {
                "genome": {
                  "type": "string",
                  "default": "GRCh38",
                  "description": "Reference genome identifier used for read alignment.",
                  "enum": ["GRCh38", "GRCh37"]
                }
              }
            }
        """.trimIndent()

        val param = NextflowSchemaHoverParser.findParam(json, "genome")

        assertEquals("string", param?.type)
        assertEquals("GRCh38", param?.defaultValue)
        assertEquals("Reference genome identifier used for read alignment.", param?.description)
        assertEquals(listOf("GRCh38", "GRCh37"), param?.enumValues)
    }

    @Test
    fun `extracts schema parameter names`() {
        val json = """
            {
              "properties": {
                "genome": { "type": "string" },
                "reads": { "type": "string" }
              }
            }
        """.trimIndent()

        assertEquals(listOf("genome", "reads"), NextflowSchemaHoverParser.findParamNames(json))
    }
}
