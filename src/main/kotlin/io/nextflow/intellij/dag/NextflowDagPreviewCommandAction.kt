package io.nextflow.intellij.dag

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.redhat.devtools.lsp4ij.commands.LSPCommand
import com.redhat.devtools.lsp4ij.commands.LSPCommandAction

/**
 * Handles the `nextflow.previewDag` command sent by the Nextflow Language Server
 * as a code lens. LSP4IJ resolves commands by looking up IntelliJ actions whose
 * id matches the command string, so this action must be registered with
 * `id="nextflow.previewDag"`.
 */
class NextflowDagPreviewCommandAction : LSPCommandAction() {
    override fun commandPerformed(command: LSPCommand, event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = event.getData(CommonDataKeys.EDITOR)
        NextflowDagPreviewService.preview(project, file, editor?.caretModel?.logicalPosition?.line ?: 0, command)
    }
}
