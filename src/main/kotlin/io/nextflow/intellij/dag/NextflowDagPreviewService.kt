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
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.services.LanguageServer
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

object NextflowDagPreviewService {
    private const val SERVER_ID = "io.nextflow.languageServer"
    private const val PREVIEW_DAG_COMMAND = "nextflow.server.previewDag"
    private val LOG = Logger.getInstance(NextflowDagPreviewService::class.java)

    fun preview(project: Project, sourceFile: VirtualFile, caretLine: Int) {
        val manager = LanguageServerManager.getInstance(project)
        manager.getLanguageServer(SERVER_ID).thenAccept { item ->
            if (item == null) {
                notify(project, "Open a Nextflow file to start the language server.", NotificationType.WARNING)
                return@thenAccept
            }

            item.initializedServer
                .thenCompose { server -> findDagCommand(server, sourceFile, caretLine).thenCompose { command -> executeDagCommand(server, command) } }
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
    ): CompletableFuture<Command> {
        val params = CodeLensParams(TextDocumentIdentifier(sourceFile.url))
        return server.textDocumentService.codeLens(params).thenCompose { lenses ->
            val lens = lenses
                .orEmpty()
                .filter { it.isPreviewDagLens() }
                .minByOrNull { abs((it.range?.start?.line ?: caretLine) - caretLine) }
                ?: return@thenCompose CompletableFuture.failedFuture(
                    IllegalStateException("No Preview DAG code lens found for this file.")
                )

            if (lens.command != null) {
                CompletableFuture.completedFuture(lens.command)
            } else {
                server.textDocumentService.resolveCodeLens(lens).thenApply { resolved ->
                    resolved.command ?: throw IllegalStateException("Preview DAG code lens did not resolve to a command.")
                }
            }
        }
    }

    private fun executeDagCommand(server: LanguageServer, command: Command): CompletableFuture<DagPreviewResult> {
        val commandId = command.command ?: PREVIEW_DAG_COMMAND
        val arguments = command.arguments.orEmpty()
        return server.workspaceService
            .executeCommand(ExecuteCommandParams(commandId, arguments))
            .thenApply { DagPreviewResult(it, commandId, arguments) }
    }

    private fun CodeLens.isPreviewDagLens(): Boolean {
        val command = command
        return command?.command == PREVIEW_DAG_COMMAND ||
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
}

private data class NamedLocation(val name: String, val location: Location)

data class DagPreviewResult(
    val payload: Any?,
    val command: String,
    val arguments: List<Any>,
) {
    fun toMermaid(): String = when (payload) {
        null -> ""
        is String -> payload
        is Map<*, *> -> (payload["mermaid"] ?: payload["diagram"] ?: payload["value"] ?: payload).toString()
        else -> payload.toString()
    }
}

fun String.isNextflowScriptPath(): Boolean = endsWith(".nf") || endsWith(".nf.test")
