package io.nextflow.intellij.lsp

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class NextflowLspProtocolTest : BasePlatformTestCase() {
    fun testScriptFileUsesNextflowLanguageId() {
        val file = myFixture.addFileToProject("main.nf", "workflow { }").virtualFile

        assertEquals("nextflow", file.nextflowLanguageId())
    }

    fun testConfigFileUsesNextflowConfigLanguageId() {
        val file = myFixture.addFileToProject("nextflow.config", "process.executor = 'local'").virtualFile

        assertEquals("nextflow-config", file.nextflowLanguageId())
    }

    fun testLspUriUsesStandardsCompliantFileUri() {
        val file = myFixture.addFileToProject("nested/main.nf", "workflow { }").virtualFile

        assertEquals(file.toNioPath().toUri().toString(), file.toLspUriString())
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
}
