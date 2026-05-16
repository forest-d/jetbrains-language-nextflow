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
            LOG.warn("RESTART[0] Restarting Nextflow language server")
            LOG.warn("RESTART[1] Calling manager.stop()")
            manager.stop(NextflowLspRuntime.SERVER_ID)
            LOG.warn("RESTART[2] stop() returned, calling manager.start()")
            manager.start(NextflowLspRuntime.SERVER_ID)
            LOG.warn("RESTART[3] start() returned, scheduling warm-up")
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

    private fun warmUpAfterRestart(project: Project, attempt: Int = 1) {
        LOG.warn("RESTART[4] warmUpAfterRestart scheduled, attempt=$attempt, delay=${WARM_UP_DELAY_MS}ms")
        CompletableFuture.delayedExecutor(WARM_UP_DELAY_MS, TimeUnit.MILLISECONDS).execute {
            LOG.warn("RESTART[5] delay elapsed, calling getLanguageServer(), attempt=$attempt")
            LanguageServerManager.getInstance(project)
                .getLanguageServer(NextflowLspRuntime.SERVER_ID)
                .thenAccept { item ->
                    LOG.warn("RESTART[6] getLanguageServer() resolved: item=${if (item != null) "PRESENT" else "NULL"}, attempt=$attempt")
                    if (item != null) {
                        LOG.warn("RESTART[7] calling warmUpProject(forceDocumentSync=true), attempt=$attempt")
                        NextflowLspRuntime.warmUpProject(project, forceDocumentSync = true)
                    } else if (attempt < WARM_UP_MAX_ATTEMPTS) {
                        LOG.warn("RESTART[7] server not ready, scheduling retry attempt=${attempt + 1}")
                        warmUpAfterRestart(project, attempt + 1)
                    } else {
                        LOG.warn("RESTART[7] GAVE UP after $attempt attempts - server never became available")
                    }
                }
                .exceptionally { error ->
                    LOG.warn("RESTART[ERR] warmUpAfterRestart failed, attempt=$attempt", error)
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
