package io.nextflow.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.nextflow.intellij.NextflowNotifications
import io.nextflow.intellij.lsp.LanguageServerDownloader
import io.nextflow.intellij.lsp.NextflowRealtimeDiagnostics
import io.nextflow.intellij.settings.NextflowSettings

class NextflowStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        LOG.info("Nextflow plugin activated for project: ${project.name}")
        NextflowRealtimeDiagnostics.install(project)

        val versionPrefix = NextflowSettings.getInstance().state.languageServerVersion.versionPrefix
        LanguageServerDownloader.ensureDownloaded(versionPrefix) { path ->
            if (path != null) {
                LOG.info("Language server ready: $path")
            } else {
                LOG.warn("Language server not available")
                NextflowNotifications.warn(
                    project,
                    "Nextflow language server is not available. Check your network connection or cached language server version."
                )
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(NextflowStartupActivity::class.java)
    }
}
