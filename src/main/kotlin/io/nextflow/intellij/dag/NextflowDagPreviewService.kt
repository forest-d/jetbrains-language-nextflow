package io.nextflow.intellij.dag

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.commands.LSPCommand
import io.nextflow.intellij.lsp.NextflowLspRuntime
import io.nextflow.intellij.lsp.isNextflowPath
import io.nextflow.intellij.lsp.toLspUriString
import io.nextflow.intellij.lsp.toVirtualFile
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageServer
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
                    NextflowLspRuntime.ensureWorkspaceInitialized(project, server)
                        .thenCompose { NextflowLspRuntime.ensureDocumentSynchronized(server, sourceFile) }
                        .thenCompose { findDagCommand(server, sourceFile, caretLine) }
                }
                .thenCompose { command -> executeDagCommand(command.server, command.command) }
                .thenAccept { result ->
                    val mermaid = result.toMermaid()
                    ApplicationManager.getApplication().invokeLater {
                        val previewFile = NextflowDagPreviewFile(sourceFile, mermaid, caretLine, result.command, result.arguments)
                        FileEditorManager.getInstance(project).openFile(previewFile, true)
                    }
                }
                .exceptionally { error ->
                    LOG.warn("Failed to preview Nextflow DAG", error)
                    notify(project, "Unable to generate DAG preview: ${error.rootMessage()}", NotificationType.ERROR)
                    null
                }
        }.exceptionally { error ->
            LOG.warn("Failed to access Nextflow language server", error)
            notify(project, "Nextflow language server is unavailable.", NotificationType.ERROR)
            null
        }
    }

    fun previewFromCodeLens(project: Project, sourceFile: VirtualFile, caretLine: Int, codeLensCommand: LSPCommand) {
        val manager = LanguageServerManager.getInstance(project)
        manager.getLanguageServer(SERVER_ID).thenAccept { item ->
            if (item == null) {
                notify(project, "Open a Nextflow file to start the language server.", NotificationType.WARNING)
                return@thenAccept
            }

            item.initializedServer
                .thenCompose { server ->
                    NextflowLspRuntime.ensureWorkspaceInitialized(project, server)
                        .thenCompose { NextflowLspRuntime.ensureDocumentSynchronized(server, sourceFile) }
                        .thenCompose {
                            val command = Command(codeLensCommand.title, codeLensCommand.command, codeLensCommand.originalArguments)
                            LOG.debug("NEXTFLOW_DAG execute-clicked-codelens command=${command.describe()}")
                            executeDagCommand(server, command)
                        }
                }
                .thenAccept { result ->
                    val mermaid = result.toMermaid()
                    ApplicationManager.getApplication().invokeLater {
                        val previewFile = NextflowDagPreviewFile(sourceFile, mermaid, caretLine, result.command, result.arguments)
                        FileEditorManager.getInstance(project).openFile(previewFile, true)
                    }
                }
                .exceptionally { error ->
                    LOG.warn("Failed to preview Nextflow DAG from CodeLens", error)
                    notify(project, "Unable to generate DAG preview: ${error.rootMessage()}", NotificationType.ERROR)
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
                    NextflowLspRuntime.ensureDocumentSynchronized(server, previewFile.sourceFile)
                        .thenCompose { findDagCommand(server, previewFile.sourceFile, previewFile.caretLine) }
                        .thenCompose { command -> executeDagCommand(command.server, command.command) }
                }
            }
        }
    }

    fun navigateToSymbol(project: Project, sourceFile: VirtualFile, label: String) {
        val candidates = label.symbolCandidates()
        LOG.debug("NEXTFLOW_DAG node-click label='$label' candidates=${candidates.joinToString(prefix = "[", postfix = "]")}")
        if (candidates.isEmpty()) return

        for (symbolName in candidates) {
            findLocalSymbol(project, sourceFile, symbolName)?.let { location ->
                ApplicationManager.getApplication().invokeLater {
                    OpenFileDescriptor(project, location.file, location.line, location.character).navigate(true)
                }
                return
            }
        }

        LOG.debug("NEXTFLOW_DAG node-click-no-local-target label='$label'")
    }

    private fun findLocalSymbol(project: Project, sourceFile: VirtualFile, symbolName: String): SourceLocation? {
        val files = mutableListOf<VirtualFile>()
        if (sourceFile.path.isNextflowPath()) files.add(sourceFile)
        ProjectFileIndex.getInstance(project).iterateContent { file ->
            if (!file.isDirectory && file.path.isNextflowPath() && file !in files) files.add(file)
            true
        }

        val declarationRegex = Regex("""\b(?:process|workflow|def)\s+${Regex.escape(symbolName)}\b""")
        for (file in files) {
            val text = runCatching { VfsUtilCore.loadText(file) }.getOrNull() ?: continue
            val match = declarationRegex.find(text) ?: continue
            val prefix = text.substring(0, match.range.first)
            val line = prefix.count { it == '\n' }
            val lineStart = prefix.lastIndexOf('\n').let { if (it == -1) 0 else it + 1 }
            val character = match.range.first - lineStart
            LOG.debug("NEXTFLOW_DAG node-click-local-target symbol='$symbolName' file=${file.path}:$line:$character")
            return SourceLocation(file, line, character)
        }
        LOG.debug("NEXTFLOW_DAG node-click-local-miss symbol='$symbolName'")
        return null
    }

    private fun findDagCommand(
        server: LanguageServer,
        sourceFile: VirtualFile,
        caretLine: Int,
    ): CompletableFuture<DagCommand> {
        val uri = sourceFile.toLspUriString()
        return requestCodeLenses(server, sourceFile, uri, attempt = 1)
            .thenCompose { lenses ->
                val lens = lenses
                    .filter { it.isPreviewDagLens() }
                    .minByOrNull { abs((it.range?.start?.line ?: caretLine) - caretLine) }
                    ?: return@thenCompose CompletableFuture.failedFuture(
                        IllegalStateException("Language server returned no Preview DAG code lens for $uri after $CODE_LENS_ATTEMPTS direct textDocument/codeLens requests.")
                    )

                LOG.debug(
                    "NEXTFLOW_DAG selected-lens " +
                        "caretLine=$caretLine range=${lens.range?.start?.line}:${lens.range?.start?.character} " +
                        "command=${lens.command?.describe()}"
                )

                server.textDocumentService.resolveCodeLens(lens)
                    .handle { resolved, error ->
                        if (error != null) {
                            val command = lens.command ?: throw IllegalStateException("Preview DAG code lens did not resolve to a command.", error)
                            LOG.debug("NEXTFLOW_DAG resolve-failed-using-original command=${command.describe()}", error)
                            DagCommand(server, command)
                        } else {
                            val command = resolved.command ?: lens.command
                                ?: throw IllegalStateException("Preview DAG code lens did not resolve to a command.")
                            LOG.debug("NEXTFLOW_DAG resolved-lens command=${command.describe()}")
                            DagCommand(server, command)
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
            .thenApply {
                LOG.debug("NEXTFLOW_DAG execute-command id=$commandId args=${arguments.joinToString(prefix = "[", postfix = "]")}")
                val result = DagPreviewResult(it, commandId, arguments)
                result.throwIfError()
                result
            }
    }

    private fun CodeLens.isPreviewDagLens(): Boolean {
        val command = command
        return command?.command == PREVIEW_DAG_COMMAND ||
            command?.command == PREVIEW_DAG_CODE_LENS_COMMAND ||
            command?.title?.contains("Preview DAG", ignoreCase = true) == true
    }

    private fun Command.describe(): String {
        return "title='$title' id='$command' args=${arguments.orEmpty().joinToString(prefix = "[", postfix = "]")}"
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Nextflow")
            .createNotification(content, type)
            .notify(project)
    }

    private fun String.symbolCandidates(): List<String> {
        val normalized = trim()
            .removePrefix("[")
            .removeSuffix("]")
            .removePrefix("(")
            .removeSuffix(")")
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalized.isBlank()) return emptyList()

        val tokens = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
            .findAll(normalized)
            .map { it.value }
            .filterNot { it in setOf("process", "workflow", "def", "params", "Channel") }
            .toList()
        return (listOf(normalized) + tokens.filter { it.any(Char::isUpperCase) || it.contains('_') })
            .distinct()
    }

    private fun Throwable.rootMessage(): String {
        var current: Throwable = this
        while (current.cause != null) {
            current = current.cause!!
        }
        return current.message ?: "unknown error"
    }
}

private data class DagCommand(val server: LanguageServer, val command: Command)
private data class SourceLocation(val file: VirtualFile, val line: Int, val character: Int)

data class DagPreviewResult(
    val payload: Any?,
    val command: String,
    val arguments: List<Any>,
) {
    fun throwIfError() {
        errorMessage()?.let { throw IllegalStateException(it) }
    }

    fun errorMessage(): String? = when (payload) {
        is Map<*, *> -> payload["error"]?.toString()
            ?: (payload["result"] as? Map<*, *>)?.get("error")?.toString()
            ?: (payload["value"] as? Map<*, *>)?.get("error")?.toString()
        else -> null
    }

    fun toMermaid(): String {
        throwIfError()
        return when (payload) {
            is String -> payload
            is Map<*, *> -> {
                val mermaid = payload["mermaid"] ?: payload["diagram"] ?: payload["result"] ?: payload["value"]
                mermaid as? String
                    ?: throw IllegalStateException("DAG preview command did not return Mermaid text.")
            }
            null -> throw IllegalStateException("DAG preview command returned no Mermaid text.")
            else -> throw IllegalStateException("DAG preview command returned unsupported payload type: ${payload.javaClass.name}.")
        }
    }
}
