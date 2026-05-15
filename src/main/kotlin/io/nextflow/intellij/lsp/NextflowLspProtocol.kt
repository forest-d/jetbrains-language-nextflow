package io.nextflow.intellij.lsp

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.eclipse.lsp4j.Location
import java.net.URI
import java.nio.file.Path

fun VirtualFile.toLspUriString(): String {
    return toLspPath()?.toUri()?.toString() ?: url
}

fun VirtualFile.toLspPath(): Path? {
    return runCatching { toNioPath() }.getOrNull()
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

fun String.isNextflowScriptPath(): Boolean =
    endsWith(".nf") || endsWith(".nf.test")

fun isNextflowFile(name: String): Boolean =
    name.endsWith(".nf") || name.endsWith(".nf.test") || name == "nextflow.config"
