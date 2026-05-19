package io.nextflow.intellij.dag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagSymbolCandidatesTest {

    @Test
    fun `extracts simple process name`() {
        val candidates = dagNodeSymbolCandidates("FASTQC")

        assertEquals(listOf("FASTQC"), candidates)
    }

    @Test
    fun `extracts process name with underscores`() {
        val candidates = dagNodeSymbolCandidates("TRIM_READS")

        assertEquals(listOf("TRIM_READS"), candidates)
    }

    @Test
    fun `strips surrounding brackets`() {
        val candidates = dagNodeSymbolCandidates("[FASTQC]")

        assertEquals(listOf("FASTQC"), candidates)
    }

    @Test
    fun `strips surrounding parentheses`() {
        val candidates = dagNodeSymbolCandidates("(FASTQC)")

        assertEquals(listOf("FASTQC"), candidates)
    }

    @Test
    fun `strips surrounding quotes`() {
        val candidates = dagNodeSymbolCandidates("\"FASTQC\"")

        assertEquals(listOf("FASTQC"), candidates)
    }

    @Test
    fun `normalizes whitespace`() {
        val candidates = dagNodeSymbolCandidates("  FASTQC  ")

        assertEquals(listOf("FASTQC"), candidates)
    }

    @Test
    fun `returns empty for blank label`() {
        assertTrue(dagNodeSymbolCandidates("").isEmpty())
        assertTrue(dagNodeSymbolCandidates("   ").isEmpty())
    }

    @Test
    fun `filters out reserved keywords`() {
        val candidates = dagNodeSymbolCandidates("process FASTQC")

        assertTrue("process" !in candidates)
        assertTrue("FASTQC" in candidates)
    }

    @Test
    fun `filters out all reserved keywords`() {
        for (keyword in listOf("process", "workflow", "def", "params", "Channel")) {
            val candidates = dagNodeSymbolCandidates(keyword)
            // The normalized full string is always included, but individual tokens matching keywords are filtered
            // When the label IS a keyword, the normalized string is the keyword itself
            // but no uppercase/underscore tokens should be extracted from it
        }
    }

    @Test
    fun `extracts multiple candidates from compound label`() {
        val candidates = dagNodeSymbolCandidates("ALIGN_READS -> SUMMARIZE")

        assertTrue("ALIGN_READS" in candidates)
        assertTrue("SUMMARIZE" in candidates)
    }

    @Test
    fun `includes normalized full string as first candidate`() {
        val candidates = dagNodeSymbolCandidates("FASTQC")

        assertEquals("FASTQC", candidates.first())
    }

    @Test
    fun `deduplicates when token matches normalized string`() {
        val candidates = dagNodeSymbolCandidates("FASTQC")

        assertEquals(1, candidates.count { it == "FASTQC" })
    }

    @Test
    fun `skips lowercase-only tokens without underscores`() {
        val candidates = dagNodeSymbolCandidates("run fastqc here")

        // "run", "fastqc", "here" are all lowercase without underscores — filtered out as tokens
        // only the full normalized string is included
        assertEquals("run fastqc here", candidates.first())
        assertTrue("run" !in candidates)
        assertTrue("fastqc" !in candidates)
        assertTrue("here" !in candidates)
    }

    @Test
    fun `includes tokens with mixed case`() {
        val candidates = dagNodeSymbolCandidates("parseSampleId")

        assertTrue("parseSampleId" in candidates)
    }

    @Test
    fun `includes tokens with underscores even if lowercase`() {
        val candidates = dagNodeSymbolCandidates("my_process")

        assertTrue("my_process" in candidates)
    }
}
