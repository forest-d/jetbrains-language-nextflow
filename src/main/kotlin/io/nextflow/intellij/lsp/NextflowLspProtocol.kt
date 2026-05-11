package io.nextflow.intellij.lsp

import com.intellij.openapi.vfs.VirtualFile

fun VirtualFile.toLspUriString(): String {
    return toNioPath()?.toUri()?.toString() ?: url
}

fun VirtualFile.nextflowLanguageId(): String {
    return if (name == "nextflow.config") "nextflow-config" else "nextflow"
}
