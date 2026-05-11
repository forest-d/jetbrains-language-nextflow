package io.nextflow.intellij.lsp

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.file.Path

object JavaFinder {
    private val LOG = Logger.getInstance(JavaFinder::class.java)
    private const val MIN_JAVA_VERSION = 17

    /**
     * Find a Java executable suitable for running the language server.
     * Search order: JAVA_HOME env → PATH.
     */
    fun findJava(): String? {
        val executable = if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"

        // 1. Check JAVA_HOME
        val javaHome = System.getenv("JAVA_HOME")
        if (!javaHome.isNullOrBlank()) {
            val javaPath = Path.of(javaHome, "bin", executable).toString()
            if (File(javaPath).isFile) {
                LOG.info("Found Java via JAVA_HOME: $javaPath")
                return javaPath
            }
            LOG.warn("JAVA_HOME is set to $javaHome but $javaPath does not exist")
        }

        // 2. Check PATH
        val pathEnv = System.getenv("PATH") ?: return null
        for (dir in pathEnv.split(File.pathSeparator)) {
            val javaPath = Path.of(dir, executable).toString()
            if (File(javaPath).isFile) {
                LOG.info("Found Java on PATH: $javaPath")
                return javaPath
            }
        }

        LOG.warn("No Java executable found")
        return null
    }

    /**
     * Check that the given Java executable is at least version [MIN_JAVA_VERSION].
     */
    fun checkVersion(javaPath: String): Boolean {
        return try {
            val process = ProcessBuilder(javaPath, "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            val match = Regex("""version "(\d+)""").find(output)
            val majorVersion = match?.groupValues?.get(1)?.toIntOrNull()
            if (majorVersion == null) {
                LOG.warn("Could not parse Java version from: $output")
                false
            } else {
                val ok = majorVersion >= MIN_JAVA_VERSION
                if (!ok) LOG.warn("Java $majorVersion found but $MIN_JAVA_VERSION+ is required")
                ok
            }
        } catch (e: Exception) {
            LOG.warn("Failed to check Java version: ${e.message}")
            false
        }
    }
}
