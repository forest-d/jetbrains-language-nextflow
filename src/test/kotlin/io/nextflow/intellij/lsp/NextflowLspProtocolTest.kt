package io.nextflow.intellij.lsp

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.LightVirtualFile
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class NextflowLspProtocolTest : BasePlatformTestCase() {

    // -----------------------------------------------------------------------
    // Document synchronization: force flag behavior
    //
    // These tests guard the interaction between ensureDocumentSynchronized()
    // and editor-managed files. Getting this wrong silently kills CodeLens
    // ("Preview DAG") — either by sending a duplicate didOpen (force=false
    // broken) or by never sending didOpen to a new server after restart
    // (force=true broken). See NextflowLspRuntime.ensureDocumentSynchronized.
    // -----------------------------------------------------------------------

    fun testDocumentSyncSkipsEditorManagedFileByDefault() {
        val file = myFixture.addFileToProject("main.nf", "workflow { }").virtualFile
        myFixture.openFileInEditor(file)

        val didOpen = AtomicReference<DidOpenTextDocumentParams>()
        val server = languageServerCapturingDidOpen(didOpen)

        NextflowLspRuntime.ensureDocumentSynchronized(server, file).get(5, TimeUnit.SECONDS)

        assertNull(
            "didOpen must NOT be sent for editor-managed files (force=false) — " +
                "sending it resets server document state and kills CodeLens",
            didOpen.get()
        )
    }

    fun testDocumentSyncForceSyncsEditorManagedFile() {
        val file = myFixture.addFileToProject("main.nf", "workflow { main: }").virtualFile
        myFixture.openFileInEditor(file)

        val didOpen = AtomicReference<DidOpenTextDocumentParams>()
        val server = languageServerCapturingDidOpen(didOpen)

        NextflowLspRuntime.ensureDocumentSynchronized(server, file, force = true).get(5, TimeUnit.SECONDS)

        assertNotNull(
            "didOpen MUST be sent when force=true (after server restart) — " +
                "without it the new server never learns about the active file and CodeLens disappears",
            didOpen.get()
        )
        assertEquals(file.toLspUriString(), didOpen.get().textDocument.uri)
        assertEquals("nextflow", didOpen.get().textDocument.languageId)
        assertEquals("workflow { main: }", didOpen.get().textDocument.text)
    }

    fun testNormalStartupWarmUpDoesNotSendDidOpenForProjectFiles() {
        myFixture.addFileToProject("main.nf", "workflow { }")

        val didOpenCount = AtomicInteger(0)
        val documentSymbolCount = AtomicInteger(0)
        val server = languageServerCapturingWarmUpCalls(didOpenCount, documentSymbolCount)

        NextflowLspRuntime.warmUpInitializedServer(project, server).get(5, TimeUnit.SECONDS)

        assertEquals(
            "Normal startup warm-up must not synthesize didOpen for project files. " +
                "LSP4IJ owns document lifecycle during startup; an extra didOpen can reset server state " +
                "after CodeLens appears, making Preview DAG vanish.",
            0,
            didOpenCount.get()
        )
        assertTrue(
            "Startup warm-up should still request document symbols without taking over document lifecycle",
            documentSymbolCount.get() > 0
        )
    }

    fun testForcedWarmUpSendsDidOpenForProjectFilesAfterRestart() {
        myFixture.addFileToProject("main.nf", "workflow { }")

        val didOpenCount = AtomicInteger(0)
        val documentSymbolCount = AtomicInteger(0)
        val server = languageServerCapturingWarmUpCalls(didOpenCount, documentSymbolCount)

        NextflowLspRuntime.warmUpInitializedServer(project, server, forceDocumentSync = true).get(5, TimeUnit.SECONDS)

        assertTrue(
            "Forced warm-up after language-server restart must re-send didOpen for project files",
            didOpenCount.get() > 0
        )
        assertTrue(documentSymbolCount.get() > 0)
    }

    fun testScriptFileUsesNextflowLanguageId() {
        val file = myFixture.addFileToProject("main.nf", "workflow { }").virtualFile

        assertEquals("nextflow", file.nextflowLanguageId())
    }

    fun testConfigFileUsesNextflowConfigLanguageId() {
        val file = myFixture.addFileToProject("nextflow.config", "process.executor = 'local'").virtualFile

        assertEquals("nextflow-config", file.nextflowLanguageId())
    }

    fun testLspUriReturnsNonBlankString() {
        val file = myFixture.addFileToProject("nested/main.nf", "workflow { }").virtualFile
        val uri = file.toLspUriString()

        assertTrue("LSP URI should not be blank", uri.isNotBlank())
        assertTrue("LSP URI should contain the file path, got: $uri", uri.contains("main.nf"))
    }

    fun testLspUriFallsBackForLightVirtualFile() {
        val file = LightVirtualFile("main.nf", "workflow { }")

        assertEquals(file.url, file.toLspUriString())
        assertNull(file.toLspPath())
    }

    fun testDocumentSynchronizationReadsDocumentFromBackgroundThread() {
        val file = myFixture.addFileToProject("main.nf", "workflow { main: }").virtualFile
        val didOpen = AtomicReference<DidOpenTextDocumentParams>()
        val server = languageServerCapturingDidOpen(didOpen)

        NextflowLspRuntime.ensureDocumentSynchronized(server, file).get(5, TimeUnit.SECONDS)

        assertEquals(file.toLspUriString(), didOpen.get().textDocument.uri)
        assertEquals("nextflow", didOpen.get().textDocument.languageId)
        assertEquals("workflow { main: }", didOpen.get().textDocument.text)
    }

    private fun languageServerCapturingDidOpen(
        didOpen: AtomicReference<DidOpenTextDocumentParams>,
    ): LanguageServer {
        val textDocumentService = Proxy.newProxyInstance(
            TextDocumentService::class.java.classLoader,
            arrayOf(TextDocumentService::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "didOpen" -> {
                    didOpen.set(args?.first() as DidOpenTextDocumentParams)
                    null
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        } as TextDocumentService

        return Proxy.newProxyInstance(
            LanguageServer::class.java.classLoader,
            arrayOf(LanguageServer::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getTextDocumentService" -> textDocumentService
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        } as LanguageServer
    }

    private fun languageServerCapturingWarmUpCalls(
        didOpenCount: AtomicInteger,
        documentSymbolCount: AtomicInteger,
    ): LanguageServer {
        val textDocumentService = Proxy.newProxyInstance(
            TextDocumentService::class.java.classLoader,
            arrayOf(TextDocumentService::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "didOpen" -> {
                    didOpenCount.incrementAndGet()
                    null
                }
                "documentSymbol" -> {
                    documentSymbolCount.incrementAndGet()
                    CompletableFuture.completedFuture(emptyList<Any>())
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
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        } as WorkspaceService

        return Proxy.newProxyInstance(
            LanguageServer::class.java.classLoader,
            arrayOf(LanguageServer::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getTextDocumentService" -> textDocumentService
                "getWorkspaceService" -> workspaceService
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> CompletableFuture.completedFuture(null)
            }
        } as LanguageServer
    }

}
