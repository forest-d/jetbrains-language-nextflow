package io.nextflow.intellij.lsp

import java.nio.file.Files
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

    @Test
    fun `finds Java from current IDE runtime when environment is empty`() {
        val javaHome = Files.createTempDirectory("nextflow-java-home")
        Files.createDirectories(javaHome.resolve("bin"))
        Files.createFile(javaHome.resolve("bin").resolve("java"))

        assertEquals(
            javaHome.resolve("bin").resolve("java").toString(),
            JavaFinder.findJava(
                environment = emptyMap(),
                osName = "Linux",
                currentJavaHome = javaHome.toString(),
            ),
        )
    }

    @Test
    fun `prefers configured Java home over current IDE runtime`() {
        val configuredJavaHome = Files.createTempDirectory("nextflow-configured-java-home")
        Files.createDirectories(configuredJavaHome.resolve("bin"))
        Files.createFile(configuredJavaHome.resolve("bin").resolve("java"))

        val currentJavaHome = Files.createTempDirectory("nextflow-current-java-home")
        Files.createDirectories(currentJavaHome.resolve("bin"))
        Files.createFile(currentJavaHome.resolve("bin").resolve("java"))

        assertEquals(
            configuredJavaHome.resolve("bin").resolve("java").toString(),
            JavaFinder.findJava(
                configuredJavaHome = configuredJavaHome.toString(),
                environment = emptyMap(),
                osName = "Linux",
                currentJavaHome = currentJavaHome.toString(),
            ),
        )
    }
}
