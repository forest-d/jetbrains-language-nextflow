package io.nextflow.intellij.lsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.ServerStatus
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import io.nextflow.intellij.settings.NextflowSettings
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import java.nio.file.Path

class NextflowLanguageClient(project: Project) : LanguageClientImpl(project) {

    override fun handleServerStatusChanged(serverStatus: ServerStatus) {
        super.handleServerStatusChanged(serverStatus)
        if (serverStatus == ServerStatus.started) {
            initializeWorkspace()
        }
    }

    private fun initializeWorkspace() {
        val server = this.languageServer ?: return
        val rootPath = project.basePath ?: return
        val rootUri = Path.of(rootPath).toUri().toString()

        LOG.info("Initializing Nextflow workspace: $rootUri")

        server.workspaceService.didChangeConfiguration(
            DidChangeConfigurationParams(NextflowSettings.getInstance().toFlatLspSettings())
        )
        server.workspaceService.didChangeWorkspaceFolders(
            DidChangeWorkspaceFoldersParams(
                WorkspaceFoldersChangeEvent(
                    listOf(WorkspaceFolder(rootUri, project.name)),
                    emptyList(),
                )
            )
        )
    }

    companion object {
        private val LOG = Logger.getInstance(NextflowLanguageClient::class.java)
    }
}
