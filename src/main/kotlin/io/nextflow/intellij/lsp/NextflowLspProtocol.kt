package io.nextflow.intellij.lsp

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.eclipse.lsp4j.Location
import java.net.URI
import java.nio.file.Path

fun VirtualFile.toLspUriString(): String {
    return toNioPath()?.toUri()?.toString() ?: url
}

fun VirtualFile.nextflowLanguageId(): String {
    return if (name == "nextflow.config") "nextflow-config" else "nextflow"
}

fun Location.toVirtualFile(): VirtualFile? {
    return VirtualFileManager.getInstance().findFileByUrl(uri)
        ?: runCatching { VirtualFileManager.getInstance().findFileByNioPath(Path.of(URI(uri))) }.getOrNull()
}

fun String.isNextflowPath(): Boolean =
    endsWith(".nf") || endsWith(".nf.test") || endsWith("nextflow.config")
