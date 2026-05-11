package io.nextflow.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.ServerStatus
import java.awt.event.ActionEvent
import javax.swing.Timer

class NextflowStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    private var statusBar: StatusBar? = null
    private val timer = Timer(2_000) { _: ActionEvent -> statusBar?.updateWidget(ID) }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        timer.start()
    }

    override fun dispose() {
        timer.stop()
        statusBar = null
    }

    override fun getText(): String {
        val status = LanguageServerManager.getInstance(project).getServerStatus(SERVER_ID)
        return "Nextflow: ${status.toDisplayText()}"
    }

    override fun getTooltipText(): String = "Nextflow language server status"

    override fun getAlignment(): Float = 0.5f

    private fun ServerStatus?.toDisplayText(): String = when (this) {
        ServerStatus.started -> "running"
        ServerStatus.starting, ServerStatus.checking_installed, ServerStatus.installing -> "starting"
        ServerStatus.not_installed -> "not installed"
        ServerStatus.stopping -> "stopping"
        ServerStatus.stopped -> "stopped"
        ServerStatus.installed -> "ready"
        else -> "idle"
    }

    companion object {
        const val ID = "NextflowStatus"
        private const val SERVER_ID = "io.nextflow.languageServer"
    }
}
