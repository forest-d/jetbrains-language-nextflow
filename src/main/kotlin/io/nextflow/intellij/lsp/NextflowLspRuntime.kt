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
        LOG.info("Warming up Nextflow LSP for ${project.name} (forceDocumentSync=$forceDocumentSync)")
        return LanguageServerManager.getInstance(project)
            .getLanguageServer(SERVER_ID)
            .thenCompose { item ->
                if (item == null) {
                    LOG.warn("Cannot warm up Nextflow LSP: language server is not available")
                    return@thenCompose CompletableFuture.completedFuture<Void>(null)
                }
                item.initializedServer.thenCompose { server ->
                    ensureWorkspaceInitialized(project, server)
                        .thenCompose {
                            if (forceDocumentSync) {
                                synchronizeProjectDocuments(project, server, force = true)
                            } else {
                                CompletableFuture.completedFuture(null)
                            }
                        }
                        .thenCompose { requestProjectDocumentSymbols(project, server) }
                }
            }
            .exceptionally { error ->
                LOG.warn("Failed to warm up Nextflow LSP", error)
                null
            }
    }

    fun ensureWorkspaceInitialized(project: Project, server: LanguageServer): CompletableFuture<Void> {
        initializedServers[server]?.let { return it }

        val future = CompletableFuture.runAsync {
            val rootUri = project.basePath?.let { Path.of(it).toUri().toString() } ?: return@runAsync
            LOG.debug("Initializing Nextflow LSP workspace root=$rootUri")
            server.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(NextflowSettings.getInstance().toFlatLspSettings()))
            server.workspaceService.didChangeWorkspaceFolders(
                DidChangeWorkspaceFoldersParams(
                    WorkspaceFoldersChangeEvent(
                        listOf(WorkspaceFolder(rootUri, project.name)),
                        emptyList(),
                    )
                )
            )
        }

        initializedServers[server] = future
        return future
    }

    /**
     * Sends `didOpen` (or `didChange` on subsequent calls) for [file] to [server].
     *
     * **CRITICAL — read before modifying:**
     *
     * When [force] is `false` (the default), files already open in an editor are
     * SKIPPED because LSP4IJ owns their document lifecycle. Sending a duplicate
     * `didOpen` resets the server's document state, which **silently kills CodeLens
     * (including "Preview DAG")** and other computed features. This was the root
     * cause of repeated v1.0.0 regressions.
     *
     * When [force] is `true`, the editor-managed guard is bypassed. This MUST be
     * used after a server restart (`stop(willDisable=true)` + `start()`) because
     * LSP4IJ does NOT re-send `didOpen` for already-open files after a
     * disable/re-enable cycle. Without force=true, the new server never learns
     * about the active editor file and CodeLens disappears.
     *
     * **In short:**
     * - Normal warm-up (startup): do not sync project files; let LSP4IJ handle editor files
     * - After restart (version switch): `force=true` — LSP4IJ won't re-sync them
     *
     * @see NextflowLspConfigurationNotifier.warmUpAfterRestart
     */
    fun ensureDocumentSynchronized(server: LanguageServer, file: VirtualFile, force: Boolean = false): CompletableFuture<Void> {
        if (!force && isEditorManaged(file)) return CompletableFuture.completedFuture(null)

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
