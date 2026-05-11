package io.nextflow.intellij.settings

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerManager
import io.nextflow.intellij.lsp.NextflowLanguageClient
import io.nextflow.intellij.lsp.NextflowLspRuntime
import io.nextflow.intellij.lsp.isNextflowPath
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.services.LanguageServer

object NextflowLspConfigurationNotifier {
    private const val SERVER_ID = "io.nextflow.languageServer"
    private val LOG = Logger.getInstance(NextflowLspConfigurationNotifier::class.java)

    fun notifyChanged(project: Project, restartRequired: Boolean, errorModeChanged: Boolean = false) {
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

                if (errorModeChanged) {
                    refreshDiagnostics(project, server)
                }
            }.exceptionally { error ->
                LOG.debug("Failed to send Nextflow settings to language server", error)
                null
            }
        }.exceptionally { error ->
            LOG.debug("Nextflow language server is not running", error)
            null
        }
    }

    private fun refreshDiagnostics(project: Project, server: LanguageServer) {
        // Clear existing squiggles so the new filter mode takes effect immediately
        NextflowLanguageClient.getInstance(project)?.clearDiagnostics()

        // Re-sync open Nextflow files to trigger fresh diagnostics from the LS
        // (which will pass through the updated filter in NextflowLanguageClient)
        val openFiles = FileEditorManager.getInstance(project).openFiles
        for (file in openFiles) {
            if (file.path.isNextflowPath()) {
                NextflowLspRuntime.ensureDocumentSynchronized(server, file)
            }
        }
    }
}
