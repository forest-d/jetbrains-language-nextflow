package io.nextflow.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class NextflowStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = NextflowStatusBarWidget.ID

    override fun getDisplayName(): String = "Nextflow language server status"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = NextflowStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun isEnabledByDefault(): Boolean = true
}
