package io.nextflow.intellij.lsp

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.redhat.devtools.lsp4ij.LanguageServerManager
import org.eclipse.lsp4j.CompletionContext
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.CompletionTriggerKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.util.concurrent.TimeUnit

/**
 * Debug contributor that sends a direct completion request to the server.
 * Remove once completion is working correctly.
 */
class NextflowCompletionDebugContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        if (!file.name.endsWith(".nf") && !file.name.endsWith(".nf.test") && file.name != "nextflow.config") return

        val offset = parameters.offset
        val doc = parameters.editor.document
        val lineNum = doc.getLineNumber(offset)
        val lineStart = doc.getLineStartOffset(lineNum)
        val lineEnd = doc.getLineEndOffset(lineNum)
        val lineText = doc.getText(TextRange(lineStart, lineEnd))
        val prefix = doc.getText(TextRange(lineStart, offset)).trimStart()
        val position = com.redhat.devtools.lsp4ij.LSPIJUtils.toPosition(offset, doc)

        NextflowCompletionFeature.resetCount()

        val vf = file.virtualFile ?: return

        LOG.warn(buildString {
            append("=== COMPLETION REQUEST ===")
            append("\n  File           : ${file.name}")
            append("\n  URI            : ${vf.toLspUriString()}")
            append("\n  Offset         : $offset (line $lineNum, char ${position.character})")
            append("\n  Prefix         : '${prefix.take(80)}'")
            append("\n  Full line      : '${lineText.take(100)}'")
            append("\n  isAutoPopup    : ${parameters.isAutoPopup}")
            append("\n  CompletionType : ${parameters.completionType}")
            append("\n=========================")
        })

        // Direct completion request with explicit CompletionContext
        try {
            val project = file.project
            val uri = vf.toLspUriString()
            LanguageServerManager.getInstance(project)
                .getLanguageServer("io.nextflow.languageServer")
                .thenAccept { item ->
                    if (item == null) {
                        LOG.warn("  DIRECT: Language server not available")
                        return@thenAccept
                    }
                    item.initializedServer.thenAccept { server ->
                        try {
                            // Try at the actual cursor position
                            val params = CompletionParams(
                                TextDocumentIdentifier(uri),
                                Position(lineNum, position.character)
                            )
                            params.context = CompletionContext(CompletionTriggerKind.Invoked)

                            val response = server.textDocumentService
                                .completion(params)
                                .get(5, TimeUnit.SECONDS)

                            val items = when {
                                response == null -> emptyList()
                                response.isLeft -> response.left
                                else -> response.right.items
                            }

                            LOG.warn(buildString {
                                append("  DIRECT COMPLETION (cursor position):")
                                append("\n    Position     : line=$lineNum, char=${position.character}")
                                append("\n    Total items  : ${items.size}")
                                items.take(5).forEachIndexed { i, ci ->
                                    append("\n    [$i] label='${ci.label}', kind=${ci.kind}")
                                }
                            })

                            // Also try at line 0, char 0 to check if ANY completion works
                            val topParams = CompletionParams(
                                TextDocumentIdentifier(uri),
                                Position(1, 0)  // line 1, beginning of line
                            )
                            topParams.context = CompletionContext(CompletionTriggerKind.Invoked)

                            val topResponse = server.textDocumentService
                                .completion(topParams)
                                .get(5, TimeUnit.SECONDS)

                            val topItems = when {
                                topResponse == null -> emptyList()
                                topResponse.isLeft -> topResponse.left
                                else -> topResponse.right.items
                            }

                            LOG.warn(buildString {
                                append("  DIRECT COMPLETION (line 1, char 0):")
                                append("\n    Total items  : ${topItems.size}")
                                topItems.take(5).forEachIndexed { i, ci ->
                                    append("\n    [$i] label='${ci.label}', kind=${ci.kind}")
                                }
                            })

                        } catch (e: Exception) {
                            LOG.warn("  DIRECT COMPLETION FAILED: ${e.javaClass.simpleName}: ${e.message}")
                        }
                    }
                }
        } catch (e: Exception) {
            LOG.warn("  DIRECT: Error accessing server: ${e.message}")
        }
    }

    companion object {
        private val LOG = Logger.getInstance(NextflowCompletionDebugContributor::class.java)
    }
}
