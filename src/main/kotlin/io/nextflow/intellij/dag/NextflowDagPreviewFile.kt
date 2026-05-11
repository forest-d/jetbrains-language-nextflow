package io.nextflow.intellij.dag

import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

class NextflowDagPreviewFile(
    val sourceFile: VirtualFile,
    mermaid: String,
    val command: String,
    val arguments: List<Any>,
) : LightVirtualFile("${sourceFile.nameWithoutExtension} DAG.nfdag", PlainTextFileType.INSTANCE, mermaid) {
    var mermaid: String = mermaid
        private set

    fun updateMermaid(value: String) {
        mermaid = value
        setContent(this, value, false)
    }

    override fun isWritable(): Boolean = false
}
