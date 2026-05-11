package io.nextflow.intellij.dag

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.redhat.devtools.lsp4ij.LanguageServerManager
import io.nextflow.intellij.lsp.NextflowLspRuntime
import io.nextflow.intellij.lsp.nextflowLanguageId
import io.nextflow.intellij.lsp.toLspUriString
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.services.LanguageServer
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object NextflowDagPreviewService {
    private const val SERVER_ID = "io.nextflow.languageServer"
    private const val PREVIEW_DAG_COMMAND = "nextflow.server.previewDag"
    private const val PREVIEW_DAG_CODE_LENS_COMMAND = "nextflow.previewDag"
    private const val CODE_LENS_ATTEMPTS = 8
    private const val CODE_LENS_RETRY_DELAY_MS = 250L
    private val LOG = Logger.getInstance(NextflowDagPreviewService::class.java)

    fun preview(project: Project, sourceFile: VirtualFile, caretLine: Int) {
        val manager = LanguageServerManager.getInstance(project)
        manager.getLanguageServer(SERVER_ID).thenAccept { item ->
            if (item == null) {
                notify(project, "Open a Nextflow file to start the language server.", NotificationType.WARNING)
                return@thenAccept
            }

            item.initializedServer
                .thenCompose { server ->
                    LOG.info("Nextflow LSP capabilities: ${item.serverCapabilities.toDebugSummary()}")
                    LOG.info(
                        "Nextflow LSP opened documents before DAG preview: " +
                            item.serverWrapper.openedDocuments.joinToString(prefix = "[", postfix = "]") {
                                item.serverWrapper.toUriString(it.file)
                        }
                    )
                    NextflowLspRuntime.ensureWorkspaceInitialized(project, server)
                        .thenCompose { NextflowLspRuntime.ensureDocumentSynchronized(server, sourceFile) }
                        .thenCompose { findDagCommand(server, sourceFile, caretLine) }
                }
                .thenCompose { command -> executeDagCommand(command.server, command.command) }
                .thenAccept { result ->
                    val mermaid = result.toMermaid()
                    ApplicationManager.getApplication().invokeLater {
                        val previewFile = NextflowDagPreviewFile(sourceFile, mermaid, result.command, result.arguments)
                        FileEditorManager.getInstance(project).openFile(previewFile, true)
                    }
                }
                .exceptionally { error ->
                    LOG.warn("Failed to preview Nextflow DAG", error)
                    notify(project, "Unable to generate DAG preview: ${error.message ?: "unknown error"}", NotificationType.ERROR)
                    null
                }
        }.exceptionally { error ->
            LOG.warn("Failed to access Nextflow language server", error)
            notify(project, "Nextflow language server is unavailable.", NotificationType.ERROR)
            null
        }
    }

    fun refresh(project: Project, previewFile: NextflowDagPreviewFile): CompletableFuture<DagPreviewResult> {
        val manager = LanguageServerManager.getInstance(project)
        return manager.getLanguageServer(SERVER_ID).thenCompose { item ->
            if (item == null) {
                CompletableFuture.failedFuture(IllegalStateException("Nextflow language server is not running."))
            } else {
                item.initializedServer.thenCompose { server ->
                    executeDagCommand(server, Command("Preview DAG", previewFile.command, previewFile.arguments))
                }
            }
        }
    }

    fun navigateToSymbol(project: Project, label: String) {
        val symbolName = label.normalizeNodeLabel()
        if (symbolName.isBlank()) return

        val manager = LanguageServerManager.getInstance(project)
        manager.getLanguageServer(SERVER_ID).thenAccept { item ->
            if (item == null) return@thenAccept

            item.initializedServer
                .thenCompose { server -> server.workspaceService.symbol(WorkspaceSymbolParams(symbolName)) }
                .thenAccept { result ->
                    val location = result?.let { either ->
                        val symbols = if (either.isLeft) {
                            either.left.mapNotNull { it.toNamedLocation() }
                        } else {
                            either.right.mapNotNull { it.toNamedLocation() }
                        }
                        symbols.firstOrNull { it.name.equals(symbolName, ignoreCase = true) }
                            ?: symbols.firstOrNull { it.name.contains(symbolName, ignoreCase = true) }
                            ?: symbols.firstOrNull()
                    }?.location ?: return@thenAccept

                    ApplicationManager.getApplication().invokeLater {
                        val file = location.toVirtualFile() ?: return@invokeLater
                        val position = location.range?.start ?: return@invokeLater
                        OpenFileDescriptor(project, file, position.line, position.character).navigate(true)
                    }
                }
                .exceptionally { error ->
                    LOG.debug("Failed to navigate from DAG node '$label'", error)
                    null
                }
        }
    }

    private fun findDagCommand(
        server: LanguageServer,
        sourceFile: VirtualFile,
        caretLine: Int,
    ): CompletableFuture<DagCommand> {
        val uri = sourceFile.toLspUriString()
        LOG.info("Nextflow LSP direct codeLens request file=${sourceFile.path} uri=$uri languageId=${sourceFile.nextflowLanguageId()}")

        return requestCodeLenses(server, sourceFile, uri, attempt = 1)
            .thenCompose { lenses ->
                val lens = lenses
                    .filter { it.isPreviewDagLens() }
                    .minByOrNull { abs((it.range?.start?.line ?: caretLine) - caretLine) }
                    ?: return@thenCompose CompletableFuture.failedFuture(
                        IllegalStateException("Language server returned no Preview DAG code lens for $uri after $CODE_LENS_ATTEMPTS direct textDocument/codeLens requests.")
                    )

                val command = lens.command
                if (command != null) {
                    CompletableFuture.completedFuture(DagCommand(server, command))
                } else {
                    server.textDocumentService.resolveCodeLens(lens).thenApply { resolved ->
                        DagCommand(
                            server,
                            resolved.command ?: throw IllegalStateException("Preview DAG code lens did not resolve to a command.")
                        )
                    }
                }
            }
    }

    private fun requestCodeLenses(
        server: LanguageServer,
        sourceFile: VirtualFile,
        uri: String,
        attempt: Int,
    ): CompletableFuture<List<CodeLens>> {
        return server.textDocumentService
            .codeLens(CodeLensParams(TextDocumentIdentifier(uri)))
            .thenCompose { result ->
                val lenses = result.orEmpty()
                LOG.info(
                    "Nextflow LSP direct codeLens response file=${sourceFile.path} " +
                        "attempt=$attempt count=${lenses.size} commands=${lenses.map { it.command?.command to it.command?.arguments }}"
                )
                if (lenses.none { it.isPreviewDagLens() } && attempt < CODE_LENS_ATTEMPTS) {
                    CompletableFuture
                        .supplyAsync({ Unit }, CompletableFuture.delayedExecutor(CODE_LENS_RETRY_DELAY_MS, TimeUnit.MILLISECONDS))
                        .thenCompose { requestCodeLenses(server, sourceFile, uri, attempt + 1) }
                } else {
                    CompletableFuture.completedFuture(lenses)
                }
            }
    }

    private fun executeDagCommand(server: LanguageServer, command: Command): CompletableFuture<DagPreviewResult> {
        val commandId = when (command.command) {
            PREVIEW_DAG_CODE_LENS_COMMAND, null -> PREVIEW_DAG_COMMAND
            else -> command.command
        }
        val arguments = command.arguments.orEmpty()
        return server.workspaceService
            .executeCommand(ExecuteCommandParams(commandId, arguments))
            .thenApply { DagPreviewResult(it, commandId, arguments) }
    }

    private fun CodeLens.isPreviewDagLens(): Boolean {
        val command = command
        return command?.command == PREVIEW_DAG_COMMAND ||
            command?.command == PREVIEW_DAG_CODE_LENS_COMMAND ||
            command?.title?.contains("Preview DAG", ignoreCase = true) == true
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Nextflow")
            .createNotification(content, type)
            .notify(project)
    }

    private fun SymbolInformation.toNamedLocation(): NamedLocation? {
        val location = location ?: return null
        return NamedLocation(name, location)
    }

    private fun WorkspaceSymbol.toNamedLocation(): NamedLocation? {
        val location = location?.let {
            if (it.isLeft) it.left else Location(it.right.uri, null)
        } ?: return null
        return NamedLocation(name, location)
    }

    private fun Location.toVirtualFile(): VirtualFile? {
        return VirtualFileManager.getInstance().findFileByUrl(uri)
            ?: runCatching { VirtualFileManager.getInstance().findFileByNioPath(Path.of(URI(uri))) }.getOrNull()
    }

    private fun String.normalizeNodeLabel(): String {
        return trim()
            .removePrefix("[")
            .removeSuffix("]")
            .removePrefix("(")
            .removeSuffix(")")
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun ServerCapabilities?.toDebugSummary(): String {
        if (this == null) return "unavailable"
        return "documentSymbolProvider=$documentSymbolProvider, " +
            "workspaceSymbolProvider=$workspaceSymbolProvider, " +
            "codeLensProvider=$codeLensProvider, " +
            "executeCommandProvider=$executeCommandProvider"
    }
}

private data class NamedLocation(val name: String, val location: Location)
private data class DagCommand(val server: LanguageServer, val command: Command)

data class DagPreviewResult(
    val payload: Any?,
    val command: String,
    val arguments: List<Any>,
) {
    fun toMermaid(): String = when (payload) {
        null -> ""
        is String -> payload
        is Map<*, *> -> (payload["mermaid"] ?: payload["diagram"] ?: payload["result"] ?: payload["value"] ?: payload).toString()
        else -> payload.toString()
    }
}

fun String.isNextflowScriptPath(): Boolean = endsWith(".nf") || endsWith(".nf.test")
