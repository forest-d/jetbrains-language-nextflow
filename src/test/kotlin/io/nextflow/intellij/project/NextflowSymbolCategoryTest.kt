package io.nextflow.intellij.project

import org.eclipse.lsp4j.SymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals

class NextflowSymbolCategoryTest {

    // ── SymbolCategory.from() ──────────────────────────────────────

    @Test
    fun `categorizes process by name`() {
        assertEquals(SymbolCategory.PROCESSES, SymbolCategory.from("process FASTQC", SymbolKind.Function, null))
    }

    @Test
    fun `categorizes process by container name`() {
        assertEquals(SymbolCategory.PROCESSES, SymbolCategory.from("FASTQC", SymbolKind.Function, "process"))
    }

    @Test
    fun `categorizes workflow by name`() {
        assertEquals(SymbolCategory.WORKFLOWS, SymbolCategory.from("workflow QC_PIPELINE", SymbolKind.Function, null))
    }

    @Test
    fun `categorizes workflow by container name`() {
        assertEquals(SymbolCategory.WORKFLOWS, SymbolCategory.from("QC_PIPELINE", SymbolKind.Function, "workflow"))
    }

    @Test
    fun `categorizes enum by kind`() {
        assertEquals(SymbolCategory.ENUMS, SymbolCategory.from("Color", SymbolKind.Enum, null))
    }

    @Test
    fun `categorizes enum member by kind`() {
        assertEquals(SymbolCategory.ENUMS, SymbolCategory.from("RED", SymbolKind.EnumMember, null))
    }

    @Test
    fun `categorizes record by struct kind`() {
        assertEquals(SymbolCategory.RECORDS, SymbolCategory.from("SampleRecord", SymbolKind.Struct, null))
    }

    @Test
    fun `categorizes record by class kind`() {
        assertEquals(SymbolCategory.RECORDS, SymbolCategory.from("SampleRecord", SymbolKind.Class, null))
    }

    @Test
    fun `categorizes record by interface kind`() {
        assertEquals(SymbolCategory.RECORDS, SymbolCategory.from("SampleRecord", SymbolKind.Interface, null))
    }

    @Test
    fun `categorizes function by kind`() {
        assertEquals(SymbolCategory.FUNCTIONS, SymbolCategory.from("parseSampleId", SymbolKind.Function, null))
    }

    @Test
    fun `categorizes method by kind`() {
        assertEquals(SymbolCategory.FUNCTIONS, SymbolCategory.from("doSomething", SymbolKind.Method, null))
    }

    @Test
    fun `categorizes unknown symbol as other`() {
        assertEquals(SymbolCategory.OTHER, SymbolCategory.from("myVar", SymbolKind.Variable, null))
    }

    @Test
    fun `process keyword in name takes priority over function kind`() {
        assertEquals(SymbolCategory.PROCESSES, SymbolCategory.from("process FASTQC", SymbolKind.Function, null))
    }

    @Test
    fun `workflow keyword takes priority over enum kind`() {
        assertEquals(SymbolCategory.WORKFLOWS, SymbolCategory.from("workflow MAIN", SymbolKind.Enum, null))
    }

    @Test
    fun `case insensitive name matching`() {
        assertEquals(SymbolCategory.PROCESSES, SymbolCategory.from("Process FASTQC", null, null))
    }

    // ── displaySymbolName() ──────────────────────────────────────

    @Test
    fun `strips process prefix`() {
        assertEquals("FASTQC", displaySymbolName("process FASTQC"))
    }

    @Test
    fun `strips workflow prefix`() {
        assertEquals("QC_PIPELINE", displaySymbolName("workflow QC_PIPELINE"))
    }

    @Test
    fun `strips function prefix`() {
        assertEquals("parseSampleId", displaySymbolName("function parseSampleId"))
    }

    @Test
    fun `strips record prefix`() {
        assertEquals("SampleRecord", displaySymbolName("record SampleRecord"))
    }

    @Test
    fun `strips enum prefix`() {
        assertEquals("Color", displaySymbolName("enum Color"))
    }

    @Test
    fun `leaves unprefixed name unchanged`() {
        assertEquals("FASTQC", displaySymbolName("FASTQC"))
    }

    @Test
    fun `strips all matching prefixes in chain`() {
        assertEquals("nested", displaySymbolName("process workflow nested"))
    }
}
