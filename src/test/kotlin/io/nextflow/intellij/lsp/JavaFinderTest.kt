package io.nextflow.intellij.lsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JavaFinderTest {
    @Test
    fun `parses modern Java version output`() {
        val output = """openjdk version "21.0.11" 2026-04-15"""

        assertEquals(21, JavaFinder.parseMajorVersion(output))
    }

    @Test
    fun `parses legacy Java version output`() {
        val output = "java version \"1.8.0_402\""

        assertEquals(8, JavaFinder.parseMajorVersion(output))
    }

    @Test
    fun `returns null for unrecognized Java version output`() {
        assertNull(JavaFinder.parseMajorVersion("not a java version"))
    }
}
