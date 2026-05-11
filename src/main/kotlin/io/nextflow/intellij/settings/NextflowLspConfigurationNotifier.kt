package io.nextflow.intellij.settings

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerManager
import org.eclipse.lsp4j.DidChangeConfigurationParams

object NextflowLspConfigurationNotifier {
    private const val SERVER_ID = "io.nextflow.languageServer"
    private val LOG = Logger.getInstance(NextflowLspConfigurationNotifier::class.java)

    fun notifyChanged(project: Project, restartRequired: Boolean) {
        val manager = LanguageServerManager.getInstance(project)
        if (restartRequired) {
            manager.stop(SERVER_ID)
            manager.start(SERVER_ID)
            return
        }

        manager.getLanguageServer(SERVER_ID).thenAccept { item ->
            if (item == null) {
                LOG.debug("Nextflow language server is not running")
                return@thenAccept
            }

            item.initializedServer.thenAccept { server ->
                server.workspaceService.didChangeConfiguration(
                    DidChangeConfigurationParams(NextflowSettings.getInstance().toFlatLspSettings())
                )
            }.exceptionally { error ->
                LOG.debug("Failed to send Nextflow settings to language server", error)
                null
            }
        }.exceptionally { error ->
            LOG.debug("Nextflow language server is not running", error)
            null
        }
    }
}
