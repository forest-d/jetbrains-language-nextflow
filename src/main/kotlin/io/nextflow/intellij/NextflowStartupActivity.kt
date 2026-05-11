package io.nextflow.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.nextflow.intellij.lsp.LanguageServerDownloader

class NextflowStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        LOG.info("Nextflow plugin activated for project: ${project.name}")

        // Pre-download the language server JAR in the background
        LanguageServerDownloader.ensureDownloaded { path ->
            if (path != null) {
                LOG.info("Language server ready: $path")
            } else {
                LOG.warn("Language server not available")
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(NextflowStartupActivity::class.java)
    }
}
