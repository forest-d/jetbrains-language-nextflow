package io.nextflow.intellij.dag

import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Proxy-based mock [LanguageServer] with configurable canned responses
 * for codeLens, resolveCodeLens, and executeCommand requests.
 *
 * @param codeLensResponses  Successive responses to textDocument/codeLens.
 *   Once exhausted the last entry repeats on every subsequent call.
 * @param resolveCodeLens    Handler for codeLens/resolve. Default: identity.
 * @param resolveCodeLensFails  If true, resolveCodeLens returns a failed future.
 * @param executeCommandResult  The payload returned by workspace/executeCommand.
 */
internal class MockLanguageServer(
    codeLensResponses: List<List<CodeLens>> = listOf(emptyList()),
    private val resolveCodeLens: (CodeLens) -> CodeLens = { it },
    private val resolveCodeLensFails: Boolean = false,
    private val executeCommandResult: Any? = null,
) {
    val codeLensCallCount = AtomicInteger(0)
    val lastExecuteCommandParams = AtomicReference<ExecuteCommandParams>()

    private val responses = codeLensResponses.ifEmpty { listOf(emptyList()) }
    private val responseIndex = AtomicInteger(0)

    val server: LanguageServer = buildServer()

    private fun buildServer(): LanguageServer {
        val textDocService = Proxy.newProxyInstance(
            TextDocumentService::class.java.classLoader,
            arrayOf(TextDocumentService::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "codeLens" -> {
                    codeLensCallCount.incrementAndGet()
                    val idx = responseIndex.getAndIncrement().coerceAtMost(responses.size - 1)
                    CompletableFuture.completedFuture(responses[idx])
                }
                "resolveCodeLens" -> {
                    if (resolveCodeLensFails) {
                        CompletableFuture.failedFuture<CodeLens>(RuntimeException("resolve failed"))
                    } else {
                        val lens = args[0] as CodeLens
                        CompletableFuture.completedFuture(resolveCodeLens(lens))
                    }
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> CompletableFuture.completedFuture(null)
            }
        } as TextDocumentService

        val workspaceService = Proxy.newProxyInstance(
            WorkspaceService::class.java.classLoader,
            arrayOf(WorkspaceService::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "executeCommand" -> {
                    lastExecuteCommandParams.set(args[0] as ExecuteCommandParams)
                    CompletableFuture.completedFuture(executeCommandResult)
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> CompletableFuture.completedFuture(null)
            }
        } as WorkspaceService

        return Proxy.newProxyInstance(
            LanguageServer::class.java.classLoader,
            arrayOf(LanguageServer::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getTextDocumentService" -> textDocService
                "getWorkspaceService" -> workspaceService
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> CompletableFuture.completedFuture(null)
            }
        } as LanguageServer
    }
}

internal fun previewDagLens(
    line: Int,
    command: String = "nextflow.previewDag",
    title: String = "Preview DAG",
    args: List<Any> = listOf("file:///test.nf"),
): CodeLens = CodeLens(
    Range(Position(line, 0), Position(line, 0)),
    Command(title, command, args),
    null,
)
