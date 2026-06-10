package io.nextflow.intellij.lsp

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Regression guard for the editor-managed document guard in
 * [NextflowLspRuntime.ensureDocumentSynchronized].
 *
 * LSP4IJ owns the document lifecycle of files open in editors. A duplicate
 * `didOpen` from our warm-up path resets the server's document state and
 * silently kills CodeLens ("Preview DAG") — the recurring v1.0.0 regression.
 */
class NextflowLspRuntimeTest : BasePlatformTestCase() {

    /** Records every LSP method invoked on the server, returning canned empty responses. */
    private class RecordingServer {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

        val server: LanguageServer = run {
            fun <T> recordingProxy(type: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
                    when (method.name) {
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        "toString" -> "RecordingServer proxy"
                        else -> {
                            calls.add(method.name)
                            CompletableFuture.completedFuture(null)
                        }
                    }
                } as T
            }

            val textDocService = recordingProxy(TextDocumentService::class.java)
            val workspaceService = recordingProxy(WorkspaceService::class.java)
            Proxy.newProxyInstance(
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

    fun testEditorManagedFileIsNotReopenedWithoutForce() {
        val file = myFixture.configureByText("main.nf", "workflow { }").virtualFile
        assertTrue(
            "Precondition: fixture file must be open in an editor",
            FileEditorManager.getInstance(project).isFileOpen(file),
        )
        val recording = RecordingServer()

        NextflowLspRuntime.ensureDocumentSynchronized(recording.server, file, force = false)
            .get(10, TimeUnit.SECONDS)

        assertFalse("didOpen must not be sent for editor-managed files", "didOpen" in recording.calls)
        assertFalse("didChange must not be sent for editor-managed files", "didChange" in recording.calls)
    }

    fun testWarmUpRequestsSymbolsWithoutReopeningEditorManagedFile() {
        val file = myFixture.configureByText("main.nf", "workflow { }").virtualFile
        assertTrue(FileEditorManager.getInstance(project).isFileOpen(file))
        val recording = RecordingServer()

        // Same sequence the startup warm-up runs: a non-forced document sync
        // followed by project-wide documentSymbol requests.
        NextflowLspRuntime.ensureDocumentSynchronized(recording.server, file, force = false)
            .get(10, TimeUnit.SECONDS)
        NextflowLspRuntime.warmUpInitializedServer(project, recording.server)
            .get(30, TimeUnit.SECONDS)

        assertTrue("warm-up should request document symbols", "documentSymbol" in recording.calls)
        assertFalse(
            "didOpen must not be sent for editor-managed files even when symbol requests follow",
            "didOpen" in recording.calls,
        )
    }

    fun testNonEditorManagedFileIsOpenedOnFirstSync() {
        val file = myFixture.addFileToProject("modules/helper.nf", "process HELPER { }").virtualFile
        assertFalse(FileEditorManager.getInstance(project).isFileOpen(file))
        val recording = RecordingServer()

        NextflowLspRuntime.ensureDocumentSynchronized(recording.server, file, force = false)
            .get(10, TimeUnit.SECONDS)

        assertEquals(listOf("didOpen"), recording.calls)
    }

    fun testForcedSyncSendsDidOpenForEditorManagedFile() {
        val file = myFixture.configureByText("main.nf", "workflow { }").virtualFile
        assertTrue(FileEditorManager.getInstance(project).isFileOpen(file))
        val recording = RecordingServer()

        // After a server restart LSP4IJ does not re-send didOpen, so force=true
        // must bypass the editor-managed guard.
        NextflowLspRuntime.ensureDocumentSynchronized(recording.server, file, force = true)
            .get(10, TimeUnit.SECONDS)

        assertEquals(listOf("didOpen"), recording.calls)
    }
}
