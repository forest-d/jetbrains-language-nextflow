package io.nextflow.intellij.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
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
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import java.nio.file.Path
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
        TreeSpeedSearch(tree) { path -> path.lastPathComponent.toString() }

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
                server.workspaceService.symbol(WorkspaceSymbolParams(""))
            }.thenAccept { result ->
                val symbols = result?.let { either ->
                    if (either.isLeft) {
                        either.left.mapNotNull { it.toProjectSymbol() }
                    } else {
                        either.right.mapNotNull { it.toProjectSymbol() }
                    }
                }.orEmpty()

                ApplicationManager.getApplication().invokeLater {
                    if (!disposed.get()) {
                        replaceSymbols(symbols)
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

    private fun SymbolInformation.toProjectSymbol(): ProjectSymbol? {
        val location = location ?: return null
        if (!location.uri.isNextflowUri()) return null
        return ProjectSymbol(name, kind, containerName, location, SymbolCategory.from(name, kind, containerName))
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
        return ProjectSymbol(name, kind, containerName, location, SymbolCategory.from(name, kind, containerName))
    }

    private fun Location.toVirtualFile(): VirtualFile? {
        return VirtualFileManager.getInstance().findFileByUrl(uri)
            ?: runCatching { VirtualFileManager.getInstance().findFileByNioPath(Path.of(URI(uri))) }.getOrNull()
    }

    private fun String?.isNextflowUri(): Boolean {
        if (this.isNullOrBlank()) return false
        return endsWith(".nf") || endsWith(".nf.test") || endsWith("nextflow.config")
    }

    private fun String.isNextflowPath(): Boolean =
        endsWith(".nf") || endsWith(".nf.test") || endsWith("nextflow.config")

    companion object {
        private const val SERVER_ID = "io.nextflow.languageServer"
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
