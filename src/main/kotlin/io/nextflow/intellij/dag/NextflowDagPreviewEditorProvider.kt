package io.nextflow.intellij.dag

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile

class NextflowDagPreviewEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file is NextflowDagPreviewFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return NextflowDagPreviewEditor(project, file as NextflowDagPreviewFile)
    }

    override fun getEditorTypeId(): String = "nextflow-dag-preview"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
