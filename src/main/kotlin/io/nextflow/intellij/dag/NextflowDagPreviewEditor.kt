package io.nextflow.intellij.dag

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

class NextflowDagPreviewEditor(
    private val project: Project,
    private val previewFile: NextflowDagPreviewFile,
) : UserDataHolderBase(), FileEditor {
    private val disposed = AtomicBoolean(false)
    private val statusLabel = JBLabel("DAG preview")
    private val panel = JPanel(BorderLayout())
    private val browser = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private val navigationQuery = browser?.let { JBCefJSQuery.create(it as JBCefBrowserBase) }
    private val textArea = JBTextArea(previewFile.mermaid)
    private val refreshTimer = Timer(750) { refresh() }

    init {
        refreshTimer.isRepeats = false

        val toolbar = JPanel(BorderLayout())
        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener { refresh() }
        toolbar.add(statusLabel, BorderLayout.CENTER)
        toolbar.add(refreshButton, BorderLayout.EAST)
        panel.add(toolbar, BorderLayout.NORTH)

        if (browser != null) {
            navigationQuery?.addHandler { label ->
                NextflowDagPreviewService.navigateToSymbol(project, previewFile.sourceFile, label)
                JBCefJSQuery.Response(null)
            }
            panel.add(browser.component, BorderLayout.CENTER)
        } else {
            textArea.isEditable = false
            panel.add(ScrollPaneFactory.createScrollPane(textArea), BorderLayout.CENTER)
            statusLabel.text = "DAG preview source (JCEF unavailable)"
        }

        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (events.any { it.file == previewFile.sourceFile || it.path == previewFile.sourceFile.path }) {
                        refreshTimer.restart()
                    }
                }
            }
        )

        render(previewFile.mermaid)
    }

    private fun refresh() {
        if (disposed.get()) return
        statusLabel.text = "Refreshing DAG preview..."
        NextflowDagPreviewService.refresh(project, previewFile).thenAccept { result ->
            val mermaid = result.toMermaid()
            ApplicationManager.getApplication().invokeLater {
                if (!disposed.get()) {
                    previewFile.updateMermaid(mermaid)
                    render(mermaid)
                    statusLabel.text = "DAG preview"
                }
            }
        }.exceptionally { error ->
            LOG.warn("Failed to refresh Nextflow DAG preview", error)
            ApplicationManager.getApplication().invokeLater {
                if (!disposed.get()) {
                    statusLabel.text = "Unable to refresh DAG preview"
                }
            }
            null
        }
    }

    private fun render(mermaid: String) {
        textArea.text = mermaid
        browser?.loadHTML(buildHtml(mermaid))
    }

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = browser?.component ?: textArea

    override fun getName(): String = "Nextflow DAG"

    override fun setState(state: FileEditorState) = Unit

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): VirtualFile = previewFile

    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            refreshTimer.stop()
            navigationQuery?.dispose()
            browser?.dispose()
        }
    }

    private fun buildHtml(mermaid: String): String {
        val clickableMermaid = mermaid.withClickDirectives()
        val escaped = clickableMermaid.escapeHtml()
        val clickHandler = navigationQuery?.inject("label") ?: "undefined"
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                html, body { margin: 0; min-height: 100%; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
                body { padding: 16px; background: #ffffff; color: #1f2328; }
                .mermaid { width: max-content; min-width: 100%; }
                .mermaid svg .node, .mermaid svg g[id^="flowchart-"] { cursor: pointer; pointer-events: all; }
                .fallback { white-space: pre; font: 13px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
              </style>
              <script type="module">
                import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
                window.nextflowNavigate = (label) => { $clickHandler; };
                mermaid.initialize({ startOnLoad: true, theme: 'default', securityLevel: 'loose' });
                const navigate = (label) => { $clickHandler; };
                const labelFor = (node) => {
                  const label = node.querySelector('.nodeLabel, .label, text, span, foreignObject');
                  return (label?.textContent || node.textContent || '').trim();
                };
                const bindClicks = () => {
                  document.querySelectorAll('.mermaid svg .node, .mermaid svg g[id^="flowchart-"]').forEach((node) => {
                    if (node.dataset.nextflowClickBound === 'true') return;
                    node.dataset.nextflowClickBound = 'true';
                    node.addEventListener('click', (event) => {
                      event.preventDefault();
                      event.stopPropagation();
                      const label = labelFor(node);
                      if (label) navigate(label);
                    });
                  });
                  document.querySelectorAll('.mermaid svg').forEach((svg) => {
                    if (svg.dataset.nextflowSvgClickBound === 'true') return;
                    svg.dataset.nextflowSvgClickBound = 'true';
                    svg.addEventListener('click', (event) => {
                      const node = event.target.closest?.('.node, g[id^="flowchart-"]');
                      if (!node) return;
                      const label = labelFor(node);
                      if (label) navigate(label);
                    }, true);
                  });
                };
                new MutationObserver(bindClicks).observe(document.body, { childList: true, subtree: true });
                window.addEventListener('load', () => setTimeout(bindClicks, 250));
              </script>
            </head>
            <body>
              <div class="mermaid">$escaped</div>
              <noscript><pre class="fallback">$escaped</pre></noscript>
            </body>
            </html>
        """.trimIndent()
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun String.withClickDirectives(): String {
        val existingClickTargets = Regex("""(?m)^\s*click\s+([A-Za-z_][A-Za-z0-9_-]*)\b""")
            .findAll(this)
            .mapTo(mutableSetOf()) { it.groupValues[1] }
        val declarations = Regex("""(?m)^\s*([A-Za-z_][A-Za-z0-9_-]*)\s*(?:\[[^\]]*]|\([^)]*\)|\{[^}]*}|>|$)""")
            .findAll(this)
            .mapNotNull { match ->
                val id = match.groupValues[1]
                if (id in setOf("flowchart", "graph", "subgraph", "end", "classDef", "class", "style", "linkStyle", "click")) return@mapNotNull null
                val label = Regex("""["']([^"']+)["']""").find(match.value)?.groupValues?.get(1) ?: id
                id to label
            }
            .distinctBy { it.first }
            .filterNot { it.first in existingClickTargets }
            .toList()
        if (declarations.isEmpty()) return this
        return buildString {
            append(this@withClickDirectives.trimEnd())
            declarations.forEach { (id, label) ->
                append('\n')
                append("  click ")
                append(id)
                append(" call nextflowNavigate(\"")
                append(label.escapeMermaidString())
                append("\")")
            }
        }
    }

    private fun String.escapeMermaidString(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private val LOG = Logger.getInstance(NextflowDagPreviewEditor::class.java)
    }
}
