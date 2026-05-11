package io.nextflow.intellij.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.nextflow.intellij.settings.NextflowSettings
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
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

    fun ensureDocumentSynchronized(server: LanguageServer, file: VirtualFile): CompletableFuture<Void> {
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
}
