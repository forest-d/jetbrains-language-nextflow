package io.nextflow.intellij.lsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider
import io.nextflow.intellij.settings.NextflowSettings

class NextflowLanguageServer(private val project: Project) : ProcessStreamConnectionProvider() {

    companion object {
        private val LOG = Logger.getInstance(NextflowLanguageServer::class.java)
    }

    init {
        val settings = NextflowSettings.getInstance().state
        val javaPath = JavaFinder.findJava(settings.javaHome)
            ?: throw CannotStartProcessException(
                "Java not found. Install Java 17+ and ensure JAVA_HOME is set or java is on PATH."
            )

        if (!JavaFinder.checkVersion(javaPath)) {
            throw CannotStartProcessException(
                "Java 17 or later is required to run the Nextflow language server (found: $javaPath)."
            )
        }

        val serverJar = LanguageServerDownloader.getOrDownload(settings.languageServerVersion.versionPrefix)
            ?: throw CannotStartProcessException(
                "Nextflow language server JAR not available. Check your internet connection or download it manually."
            )

        val commands = listOf(javaPath, "-jar", serverJar.toString())
        LOG.info("Starting Nextflow language server: ${commands.joinToString(" ")}")

        setCommands(commands)
        setWorkingDirectory(project.basePath)
    }

    override fun getInitializationOptions(rootUri: com.intellij.openapi.vfs.VirtualFile?): Any {
        return NextflowSettings.getInstance().toLspSettings()
    }
}
