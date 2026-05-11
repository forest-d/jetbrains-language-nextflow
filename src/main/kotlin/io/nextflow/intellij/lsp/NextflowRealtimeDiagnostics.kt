package io.nextflow.intellij.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.redhat.devtools.lsp4ij.LanguageServerManager
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import java.util.WeakHashMap

object NextflowRealtimeDiagnostics {
    private const val SERVER_ID = "io.nextflow.languageServer"
    private const val DEBOUNCE_MS = 350
    private val LOG = Logger.getInstance(NextflowRealtimeDiagnostics::class.java)
    private val bridges = WeakHashMap<Project, Bridge>()

    fun install(project: Project) {
        synchronized(bridges) {
            if (bridges.containsKey(project)) return
            val bridge = Bridge(project)
            bridges[project] = bridge
            Disposer.register(project, bridge)
        }
    }

    fun createDidChangeParams(file: VirtualFile, text: String): DidChangeTextDocumentParams {
        return DidChangeTextDocumentParams(
            VersionedTextDocumentIdentifier(file.toLspUriString(), null),
            listOf(TextDocumentContentChangeEvent(text)),
        )
    }

    fun canSynchronize(file: VirtualFile): Boolean {
        return file.toLspPath() != null
    }

    private class Bridge(private val project: Project) : DocumentListener, Disposable {
        private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
        private val pending = LinkedHashMap<String, PendingChange>()

        init {
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(this, this)
        }

        override fun documentChanged(event: DocumentEvent) {
            val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
            if (!file.path.isNextflowPath()) return
            if (!canSynchronize(file)) return

            synchronized(pending) {
                pending[file.url] = PendingChange(file, event.document)
            }
            alarm.cancelAllRequests()
            alarm.addRequest({ flush() }, DEBOUNCE_MS)
        }

        private fun flush() {
            val changes = synchronized(pending) {
                val result = pending.values.toList()
                pending.clear()
                result
            }
            changes.forEach { sendDidChange(it) }
        }

        private fun sendDidChange(change: PendingChange) {
            if (project.isDisposed || !change.file.isValid) return

            val text = ApplicationManager.getApplication().runReadAction<String> {
                change.document.text
            }
            val params = createDidChangeParams(change.file, text)
            LanguageServerManager.getInstance(project)
                .getLanguageServer(SERVER_ID)
                .thenAccept { item ->
                    item?.initializedServer?.thenAccept { server ->
                        server.textDocumentService.didChange(params)
                    }?.exceptionally { error ->
                        LOG.debug("Failed to send real-time Nextflow didChange", error)
                        null
                    }
                }
                .exceptionally { error ->
                    LOG.debug("Nextflow language server is not available for real-time diagnostics", error)
                    null
                }
        }

        override fun dispose() {
            alarm.cancelAllRequests()
            synchronized(bridges) {
                bridges.remove(project)
            }
        }
    }

    private data class PendingChange(val file: VirtualFile, val document: Document)
}
