package io.nextflow.intellij.dag

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class NextflowDagPreviewAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = event.getData(CommonDataKeys.EDITOR)
        NextflowDagPreviewService.preview(project, file, editor?.caretModel?.logicalPosition?.line ?: 0)
    }

    override fun update(event: AnActionEvent) {
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = event.project != null && file?.path?.isNextflowScriptPath() == true
    }
}
