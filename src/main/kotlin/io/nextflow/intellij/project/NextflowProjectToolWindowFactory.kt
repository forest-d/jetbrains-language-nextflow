package io.nextflow.intellij.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class NextflowProjectToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val view = NextflowProjectView(project)
        val content = ContentFactory.getInstance().createContent(view.component, null, false)
        toolWindow.contentManager.addContent(content)
        Disposer.register(content, Disposable { view.dispose() })
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
