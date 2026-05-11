package io.nextflow.intellij.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBLabel
import com.intellij.ui.treeStructure.Tree
import com.redhat.devtools.lsp4ij.LanguageServerManager
import io.nextflow.intellij.lsp.NextflowLspRuntime
import io.nextflow.intellij.lsp.isNextflowPath
import io.nextflow.intellij.lsp.nextflowLanguageId
import io.nextflow.intellij.lsp.toLspUriString
import io.nextflow.intellij.lsp.toVirtualFile
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.services.LanguageServer
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.Timer
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

class NextflowProjectView(private val project: Project) : Disposable {
    private val disposed = AtomicBoolean(false)
    private val rootNode = DefaultMutableTreeNode(ProjectNode)
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    private val statusLabel = JBLabel("Loading Nextflow symbols...")
    private val refreshTimer = Timer(750) { refresh() }

    val component: JPanel = JPanel(BorderLayout())

    init {
        refreshTimer.isRepeats = false

        val toolbar = JPanel(BorderLayout())
        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener { refresh() }
        toolbar.add(statusLabel, BorderLayout.CENTER)
        toolbar.add(refreshButton, BorderLayout.EAST)

        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = SymbolTreeCellRenderer()
        tree.emptyText.text = "No Nextflow symbols"
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    navigateSelectedNode()
                }
            }
        })
        TreeSpeedSearch.installOn(tree, false) { path -> path.lastPathComponent.toString() }

        component.add(toolbar, BorderLayout.NORTH)
        component.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)

        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (events.any { it.path.isNextflowPath() }) {
                        scheduleRefresh()
                    }
                }
            }
        )

        refresh()
    }

    private fun refresh() {
        if (disposed.get()) return
        setStatus("Loading Nextflow symbols...")

        LanguageServerManager.getInstance(project).getLanguageServer(SERVER_ID).thenAccept { item ->
            if (item == null) {
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed.get()) {
                        showMessage("Open a Nextflow file to start the language server.")
                    }
                }
                return@thenAccept
            }

            item.initializedServer.thenCompose { server ->
                NextflowLspRuntime.ensureWorkspaceInitialized(project, server)
                    .thenCompose {
                        loadDocumentSymbols(server).thenCompose { documentSymbols ->
                            loadWorkspaceSymbols(server).thenApply { workspaceSymbols ->
                                (documentSymbols + workspaceSymbols)
                                    .distinctBy { "${it.name}:${it.location.uri}:${it.location.range}" }
                            }
                        }
                    }
            }.thenAccept { result ->
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed.get()) {
                        replaceSymbols(result)
                    }
                }
            }.exceptionally { error ->
                LOG.warn("Failed to load Nextflow workspace symbols", error)
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed.get()) {
                        showMessage("Unable to load Nextflow symbols.")
                    }
                }
                null
            }
        }.exceptionally { error ->
            LOG.warn("Failed to access Nextflow language server", error)
            ApplicationManager.getApplication().invokeLater {
                if (!disposed.get()) {
                    showMessage("Nextflow language server is unavailable.")
                }
            }
            null
        }
    }

    private fun loadWorkspaceSymbols(server: LanguageServer): CompletableFuture<List<ProjectSymbol>> {
        return server.workspaceService.symbol(WorkspaceSymbolParams("workflow"))
            .thenApply { result ->
                result?.let { either ->
                    if (either.isLeft) {
                        either.left.mapNotNull { it.toProjectSymbol() }
                    } else {
                        either.right.mapNotNull { it.toProjectSymbol() }
                    }
                }.orEmpty()
            }
            .exceptionally { error ->
                LOG.warn("Nextflow LSP workspace/symbol query='workflow' failed", error)
                emptyList()
            }
    }

    private fun loadDocumentSymbols(server: LanguageServer): CompletableFuture<List<ProjectSymbol>> {
        val files = nextflowFiles()
        if (files.isEmpty()) {
            return CompletableFuture.completedFuture(emptyList())
        }

        val futures: List<CompletableFuture<List<ProjectSymbol>>> = files.map { file ->
            val uri = file.toLspUriString()
            NextflowLspRuntime.ensureDocumentSynchronized(server, file)
                .thenCompose { requestDocumentSymbols(server, file, uri, attempt = 1) }
                .exceptionally { error ->
                    LOG.warn("Nextflow LSP direct documentSymbol request failed for uri=$uri", error)
                    emptyList<ProjectSymbol>()
                }
        }

        return CompletableFuture.allOf(*futures.toTypedArray()).thenApply {
            futures.flatMap { it.getNow(emptyList<ProjectSymbol>()) }
        }
    }

    private fun requestDocumentSymbols(
        server: LanguageServer,
        file: VirtualFile,
        uri: String,
        attempt: Int,
    ): CompletableFuture<List<ProjectSymbol>> {
        return server.textDocumentService
            .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
            .thenCompose { result ->
                val symbols = result.orEmpty().flatMap { either ->
                    if (either.isLeft) {
                        listOfNotNull(either.left.toProjectSymbol())
                    } else {
                        either.right.toProjectSymbols(uri)
                    }
                }
                if (symbols.isEmpty() && attempt < DOCUMENT_SYMBOL_ATTEMPTS) {
                    CompletableFuture
                        .supplyAsync({ Unit }, CompletableFuture.delayedExecutor(DOCUMENT_SYMBOL_RETRY_DELAY_MS, TimeUnit.MILLISECONDS))
                        .thenCompose { requestDocumentSymbols(server, file, uri, attempt + 1) }
                } else {
                    CompletableFuture.completedFuture(symbols)
                }
            }
    }

    private fun nextflowFiles(): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val index = ProjectFileIndex.getInstance(project)
        index.iterateContent { file ->
            if (!file.isDirectory && file.path.isNextflowPath()) {
                files.add(file)
            }
            true
        }
        return files
    }

    private fun replaceSymbols(symbols: List<ProjectSymbol>) {
        rootNode.removeAllChildren()

        val categories = symbols
            .sortedWith(compareBy<ProjectSymbol> { it.category.order }.thenBy { it.name.lowercase() })
            .groupBy { it.category }

        SymbolCategory.entries.forEach { category ->
            val items = categories[category].orEmpty()
            if (items.isNotEmpty()) {
                val categoryNode = DefaultMutableTreeNode(CategoryNode(category, items.size))
                items.forEach { categoryNode.add(DefaultMutableTreeNode(SymbolNode(it))) }
                rootNode.add(categoryNode)
            }
        }

        treeModel.reload()
        expandAll()
        setStatus("${symbols.size} symbols")
    }

    private fun showMessage(message: String) {
        rootNode.removeAllChildren()
        rootNode.add(DefaultMutableTreeNode(MessageNode(message)))
        treeModel.reload()
        setStatus(message)
    }

    private fun expandAll() {
        for (row in 0 until tree.rowCount) {
            tree.expandRow(row)
        }
    }

    private fun navigateSelectedNode() {
        val selected = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return
        val symbol = (selected.userObject as? SymbolNode)?.symbol ?: return
        val file = symbol.location.toVirtualFile() ?: return
        val range = symbol.location.range ?: return
        val position = range.start ?: return
        OpenFileDescriptor(project, file, position.line, position.character).navigate(true)
    }

    private fun scheduleRefresh() {
        refreshTimer.restart()
    }

    private fun setStatus(text: String) {
        statusLabel.text = text
    }

    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            refreshTimer.stop()
        }
    }

    @Suppress("DEPRECATION") // SymbolInformation fields are deprecated in LSP 3.17 but servers still return them
    private fun SymbolInformation.toProjectSymbol(): ProjectSymbol? {
        val loc = location ?: return null
        if (!loc.uri.isNextflowUri()) return null
        return ProjectSymbol(name.toDisplaySymbolName(), kind, containerName, loc, SymbolCategory.from(name, kind, containerName))
    }

    private fun DocumentSymbol.toProjectSymbols(uri: String, containerName: String? = null): List<ProjectSymbol> {
        val location = Location(uri, selectionRange ?: range)
        val symbol = ProjectSymbol(name.toDisplaySymbolName(), kind, containerName, location, SymbolCategory.from(name, kind, containerName))
        val childSymbols = children.orEmpty().flatMap { it.toProjectSymbols(uri, name) }
        return listOf(symbol) + childSymbols
    }

    private fun WorkspaceSymbol.toProjectSymbol(): ProjectSymbol? {
        val location = location?.let {
            if (it.isLeft) {
                it.left
            } else {
                Location(it.right.uri, null)
            }
        } ?: return null

        if (!location.uri.isNextflowUri()) return null
        return ProjectSymbol(name.toDisplaySymbolName(), kind, containerName, location, SymbolCategory.from(name, kind, containerName))
    }

    private fun String?.isNextflowUri(): Boolean {
        if (this.isNullOrBlank()) return false
        val path = runCatching { Path.of(URI(this)).toString() }.getOrDefault(this)
        return path.isNextflowPath()
    }

    private fun String.toDisplaySymbolName(): String {
        return removePrefix("process ")
            .removePrefix("workflow ")
            .removePrefix("function ")
            .removePrefix("record ")
            .removePrefix("enum ")
    }

    companion object {
        private const val SERVER_ID = "io.nextflow.languageServer"
        private const val DOCUMENT_SYMBOL_ATTEMPTS = 8
        private const val DOCUMENT_SYMBOL_RETRY_DELAY_MS = 250L
        private val LOG = Logger.getInstance(NextflowProjectView::class.java)
    }
}

private data class ProjectSymbol(
    val name: String,
    val kind: SymbolKind?,
    val containerName: String?,
    val location: Location,
    val category: SymbolCategory,
)

private enum class SymbolCategory(val title: String, val order: Int, val icon: Icon) {
    PROCESSES("Processes", 0, SymbolIcon(Color(0x2E7D32))),
    WORKFLOWS("Workflows", 1, SymbolIcon(Color(0x1565C0))),
    FUNCTIONS("Functions", 2, SymbolIcon(Color(0x6A1B9A))),
    RECORDS("Records", 3, SymbolIcon(Color(0xAD6C00))),
    ENUMS("Enums", 4, SymbolIcon(Color(0x00838F))),
    OTHER("Other Symbols", 5, SymbolIcon(Color(0x5F6368)));

    companion object {
        fun from(name: String, kind: SymbolKind?, containerName: String?): SymbolCategory {
            val haystack = listOf(name, containerName.orEmpty()).joinToString(" ").lowercase()
            return when {
                "process" in haystack -> PROCESSES
                "workflow" in haystack -> WORKFLOWS
                kind == SymbolKind.Enum || kind == SymbolKind.EnumMember -> ENUMS
                kind == SymbolKind.Struct || kind == SymbolKind.Class || kind == SymbolKind.Interface -> RECORDS
                kind == SymbolKind.Function || kind == SymbolKind.Method -> FUNCTIONS
                else -> OTHER
            }
        }
    }
}

private object ProjectNode {
    override fun toString(): String = "Nextflow"
}

private data class CategoryNode(val category: SymbolCategory, val count: Int) {
    override fun toString(): String = "${category.title} ($count)"
}

private data class SymbolNode(val symbol: ProjectSymbol) {
    override fun toString(): String {
        val container = symbol.containerName?.takeIf { it.isNotBlank() }
        return if (container == null) symbol.name else "${symbol.name}  $container"
    }
}

private data class MessageNode(val message: String) {
    override fun toString(): String = message
}

private class SymbolTreeCellRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ): Component {
        val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        val node = (value as? DefaultMutableTreeNode)?.userObject
        icon = when (node) {
            is CategoryNode -> node.category.icon
            is SymbolNode -> node.symbol.category.icon
            else -> null
        }
        return component
    }
}

private class SymbolIcon(private val color: Color) : Icon {
    override fun getIconWidth(): Int = 12

    override fun getIconHeight(): Int = 12

    override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
        graphics.color = if (component?.isEnabled == false) JBColor.GRAY else color
        graphics.fillOval(x + 2, y + 2, 8, 8)
        graphics.color = JBColor.border()
        graphics.drawOval(x + 2, y + 2, 8, 8)
    }
}
