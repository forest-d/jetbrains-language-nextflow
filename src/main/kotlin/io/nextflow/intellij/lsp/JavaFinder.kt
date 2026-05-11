package io.nextflow.intellij.lsp

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.file.Path

object JavaFinder {
    private val LOG = Logger.getInstance(JavaFinder::class.java)
    private const val MIN_JAVA_VERSION = 17

    fun findJava(configuredJavaHome: String? = null): String? {
        return findJava(
            configuredJavaHome,
            System.getenv(),
            System.getProperty("os.name"),
            System.getProperty("java.home"),
        )
    }

    internal fun findJava(
        configuredJavaHome: String? = null,
        environment: Map<String, String>,
        osName: String,
        currentJavaHome: String? = null,
    ): String? {
        val executable = if (osName.lowercase().contains("win")) "java.exe" else "java"

        // 1. Check plugin setting
        if (!configuredJavaHome.isNullOrBlank()) {
            val javaPath = Path.of(configuredJavaHome, "bin", executable).toString()
            if (File(javaPath).isFile) {
                LOG.info("Found Java via Nextflow settings: $javaPath")
                return javaPath
            }
            LOG.warn("Nextflow Java home is set to $configuredJavaHome but $javaPath does not exist")
        }

        // 2. Check JAVA_HOME
        val javaHome = environment["JAVA_HOME"]
        if (!javaHome.isNullOrBlank()) {
            val javaPath = Path.of(javaHome, "bin", executable).toString()
            if (File(javaPath).isFile) {
                LOG.info("Found Java via JAVA_HOME: $javaPath")
                return javaPath
            }
            LOG.warn("JAVA_HOME is set to $javaHome but $javaPath does not exist")
        }

        // 3. Check the IDE's own runtime. Desktop-launched IDEs often do not inherit shell env vars.
        if (!currentJavaHome.isNullOrBlank()) {
            val javaPath = Path.of(currentJavaHome, "bin", executable).toString()
            if (File(javaPath).isFile) {
                LOG.info("Found Java via IDE runtime: $javaPath")
                return javaPath
            }
            LOG.warn("IDE runtime is set to $currentJavaHome but $javaPath does not exist")
        }

        // 4. Check PATH
        val pathEnv = environment["PATH"] ?: return null
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

    fun checkVersion(javaPath: String): Boolean {
        return try {
            val process = ProcessBuilder(javaPath, "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            val majorVersion = parseMajorVersion(output)
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

    internal fun parseMajorVersion(versionOutput: String): Int? {
        val legacy = Regex("""version "1\.(\d+)""").find(versionOutput)
        if (legacy != null) {
            return legacy.groupValues[1].toIntOrNull()
        }

        val modern = Regex("""version "(\d+)""").find(versionOutput)
        return modern?.groupValues?.get(1)?.toIntOrNull()
    }
}
