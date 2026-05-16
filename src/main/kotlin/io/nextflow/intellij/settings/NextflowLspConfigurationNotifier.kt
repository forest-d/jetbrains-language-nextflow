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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object NextflowLspConfigurationNotifier {
    private val LOG = Logger.getInstance(NextflowLspConfigurationNotifier::class.java)

    private const val WARM_UP_DELAY_MS = 1000L
    private const val WARM_UP_MAX_ATTEMPTS = 8

    fun notifyChanged(project: Project, restartRequired: Boolean, errorModeChanged: Boolean = false) {
        val manager = LanguageServerManager.getInstance(project)
        if (restartRequired) {
            manager.stop(NextflowLspRuntime.SERVER_ID)
            manager.start(NextflowLspRuntime.SERVER_ID)
            warmUpAfterRestart(project)
            return
        }

        manager.getLanguageServer(NextflowLspRuntime.SERVER_ID).thenAccept { item ->
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

    /**
     * Warms up the language server after a restart with retry logic.
     *
     * **CRITICAL — `forceDocumentSync = true` is required here.**
     *
     * `stop(willDisable=true)` tears down LSP4IJ's document tracking entirely.
     * After `start(willEnable=true)`, LSP4IJ creates a new server but does NOT
     * re-send `didOpen` for files already open in editors. Without force=true,
     * the new server never learns about the active file and "Preview DAG"
     * CodeLens disappears. This was a persistent v1.0.0 regression.
     *
     * The retry loop handles the race between `start()` returning (synchronous)
     * and the new server actually being available via `getLanguageServer()`.
     */
    private fun warmUpAfterRestart(project: Project, attempt: Int = 1) {
        CompletableFuture.delayedExecutor(WARM_UP_DELAY_MS, TimeUnit.MILLISECONDS).execute {
            LanguageServerManager.getInstance(project)
                .getLanguageServer(NextflowLspRuntime.SERVER_ID)
                .thenAccept { item ->
                    if (item != null) {
                        NextflowLspRuntime.warmUpProject(project, forceDocumentSync = true)
                    } else if (attempt < WARM_UP_MAX_ATTEMPTS) {
                        warmUpAfterRestart(project, attempt + 1)
                    } else {
                        LOG.warn("Nextflow language server not available after restart ($attempt attempts)")
                    }
                }
                .exceptionally { error ->
                    LOG.warn("Failed to warm up Nextflow language server after restart", error)
                    null
                }
        }
    }

    private fun refreshDiagnostics(project: Project, server: LanguageServer) {
        NextflowLanguageClient.getInstance(project)?.clearDiagnostics()

        val openFiles = FileEditorManager.getInstance(project).openFiles
        for (file in openFiles) {
            if (file.path.isNextflowPath()) {
                NextflowLspRuntime.ensureDocumentSynchronized(server, file)
            }
        }
    }
}
