package io.nextflow.intellij.lsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.LanguageServersRegistry
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager

/**
 * Diagnostic provider that logs why hover may or may not fire.
 * Returns an empty list — it does not interfere with LSP4IJ's own provider.
 * Remove this class once hover is confirmed working.
 */
class NextflowHoverDebugProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val vf = file.virtualFile
        LOG.warn(buildString {
            append("=== HOVER DEBUG ===")
            append("\n  PsiFile class  : ${file.javaClass.name}")
            append("\n  PsiFile name   : ${file.name}")
            append("\n  Language       : ${file.language} (${file.language.javaClass.name})")
            append("\n  FileType       : ${file.fileType} (${file.fileType.javaClass.name})")
            append("\n  VirtualFile    : ${vf?.javaClass?.name ?: "NULL"}")
            append("\n  VF path        : ${vf?.path ?: "NULL"}")
            append("\n  VF isLocal     : ${vf?.isInLocalFileSystem}")
            append("\n  VF isValid     : ${vf?.isValid}")
            append("\n  Offset         : $offset")

            // Check: is this file registered with an LSP server?
            val registry = LanguageServersRegistry.getInstance()
            val supported = registry.isFileSupported(file)
            append("\n  isFileSupported: $supported")

            // Check: can we execute LSP features?
            val status = ProjectIndexingManager.canExecuteLSPFeature(file)
            append("\n  canExecuteLSP  : $status")

            // Check: FileViewProvider type
            val fvp = file.viewProvider
            append("\n  ViewProvider   : ${fvp.javaClass.name}")

            // Check: PSI children (first few)
            val children = file.children
            append("\n  PSI children   : ${children.size}")
            children.take(3).forEachIndexed { i, child ->
                append("\n    [$i] ${child.javaClass.simpleName}: '${child.text.take(50)}'")
            }

            append("\n===================")
        })
        return emptyList()
    }

    companion object {
        private val LOG = Logger.getInstance(NextflowHoverDebugProvider::class.java)
    }
}
