package io.nextflow.intellij.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LanguageServerManager
import io.nextflow.intellij.settings.NextflowSettings
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import org.eclipse.lsp4j.services.LanguageServer
import java.nio.file.Path
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture

object NextflowLspRuntime {
    private val LOG = Logger.getInstance(NextflowLspRuntime::class.java)
    private val initializedServers = Collections.synchronizedMap(WeakHashMap<LanguageServer, CompletableFuture<Void>>())
    private val synchronizedDocuments = Collections.synchronizedMap(WeakHashMap<LanguageServer, MutableMap<String, Int>>())

    fun warmUpProject(project: Project, forceDocumentSync: Boolean = false): CompletableFuture<Void> {
        LOG.warn("WARMUP[0] warmUpProject called for ${project.name}, forceDocumentSync=$forceDocumentSync")
        return LanguageServerManager.getInstance(project)
            .getLanguageServer(SERVER_ID)
            .thenCompose { item ->
                if (item == null) {
                    LOG.warn("WARMUP[1] language server item is NULL - warm-up aborted")
                    return@thenCompose CompletableFuture.completedFuture<Void>(null)
                }
                LOG.warn("WARMUP[1] language server item PRESENT, waiting for initializedServer")
                item.initializedServer.thenCompose { server ->
                    LOG.warn("WARMUP[2] server initialized: ${server.javaClass.name}@${System.identityHashCode(server)}")
                    ensureWorkspaceInitialized(project, server)
                        .thenCompose {
                            LOG.warn("WARMUP[3] workspace initialized, syncing documents (force=$forceDocumentSync)")
                            synchronizeProjectDocuments(project, server, forceDocumentSync)
                        }
                        .thenCompose {
                            LOG.warn("WARMUP[4] documents synced, requesting symbols")
                            requestProjectDocumentSymbols(project, server)
                        }
                        .thenRun {
                            LOG.warn("WARMUP[5] warm-up COMPLETE")
                        }
                }
            }
            .exceptionally { error ->
                LOG.warn("WARMUP[ERR] warm-up failed", error)
                null
            }
    }

    fun ensureWorkspaceInitialized(project: Project, server: LanguageServer): CompletableFuture<Void> {
        val serverId = "${server.javaClass.name}@${System.identityHashCode(server)}"
        initializedServers[server]?.let {
            LOG.warn("WORKSPACE already initialized for server=$serverId")
            return it
        }

        LOG.warn("WORKSPACE initializing server=$serverId")
        val future = CompletableFuture.runAsync {
            val rootUri = project.basePath?.let { Path.of(it).toUri().toString() } ?: return@runAsync
            LOG.warn("WORKSPACE sending didChangeConfiguration + didChangeWorkspaceFolders root=$rootUri")
            server.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(NextflowSettings.getInstance().toFlatLspSettings()))
            server.workspaceService.didChangeWorkspaceFolders(
                DidChangeWorkspaceFoldersParams(
                    WorkspaceFoldersChangeEvent(
                        listOf(WorkspaceFolder(rootUri, project.name)),
                        emptyList(),
                    )
                )
            )
            LOG.warn("WORKSPACE initialization sent for server=$serverId")
        }

        initializedServers[server] = future
        return future
    }

    fun ensureDocumentSynchronized(server: LanguageServer, file: VirtualFile, force: Boolean = false): CompletableFuture<Void> {
        // If the file is open in an editor, LSP4IJ manages its document lifecycle.
        // Sending a duplicate didOpen causes the language server to reset document
        // state, which clears code lenses and other computed features.
        // After a server restart, force=true bypasses this because LSP4IJ does NOT
        // re-send didOpen for already-open files after a disable/re-enable cycle.
        if (!force && isEditorManaged(file)) {
            LOG.warn("DOCSYNC skipped (editor-managed): ${file.name}")
            return CompletableFuture.completedFuture(null)
        }
        LOG.warn("DOCSYNC syncing (force=$force): ${file.name}")

        return CompletableFuture.runAsync {
            val uri = file.toLspUriString()
            val text = ApplicationManager.getApplication().runReadAction<String> {
                FileDocumentManager.getInstance().getDocument(file)?.text ?: VfsUtilCore.loadText(file)
            }
            val documents = synchronized(synchronizedDocuments) {
                synchronizedDocuments.getOrPut(server) { mutableMapOf() }
            }
            val nextVersion = synchronized(documents) {
                val version = (documents[uri] ?: 0) + 1
                documents[uri] = version
                version
            }

            if (nextVersion == 1) {
                LOG.debug("Opening LSP document uri=$uri version=$nextVersion")
                server.textDocumentService.didOpen(
                    DidOpenTextDocumentParams(
                        TextDocumentItem(uri, file.nextflowLanguageId(), nextVersion, text)
                    )
                )
            } else {
                LOG.debug("Synchronizing LSP document uri=$uri version=$nextVersion")
                server.textDocumentService.didChange(
                    DidChangeTextDocumentParams(
                        VersionedTextDocumentIdentifier(uri, nextVersion),
                        listOf(TextDocumentContentChangeEvent(text))
                    )
                )
            }

        }
    }

    private fun synchronizeProjectDocuments(project: Project, server: LanguageServer, force: Boolean = false): CompletableFuture<Void> {
        val files = nextflowFiles(project)
        if (files.isEmpty()) return CompletableFuture.completedFuture<Void>(null)

        val futures = files.map { file -> ensureDocumentSynchronized(server, file, force) }
        return CompletableFuture.allOf(*futures.toTypedArray())
    }

    private fun requestProjectDocumentSymbols(project: Project, server: LanguageServer): CompletableFuture<Void> {
        val files = nextflowFiles(project)
        if (files.isEmpty()) return CompletableFuture.completedFuture<Void>(null)

        val futures = files.map { file ->
            val uri = file.toLspUriString()
            server.textDocumentService
                .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                .thenAccept { result ->
                    LOG.debug("Warmed Nextflow document symbols uri=$uri count=${result?.size ?: 0}")
                }
                .exceptionally { error ->
                    LOG.debug("Failed to warm Nextflow document symbols uri=$uri", error)
                    null
                }
        }
        return CompletableFuture.allOf(*futures.toTypedArray())
    }

    private fun nextflowFiles(project: Project): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val index = ProjectFileIndex.getInstance(project)
        index.iterateContent { file ->
            if (!file.isDirectory && file.path.isNextflowPath()) {
                files.add(file)
            }
            true
        }
        return files
    }

    private fun isEditorManaged(file: VirtualFile): Boolean {
        return ProjectManager.getInstance().openProjects.any { project ->
            !project.isDisposed && FileEditorManager.getInstance(project).isFileOpen(file)
        }
    }

    const val SERVER_ID = "io.nextflow.languageServer"
}
