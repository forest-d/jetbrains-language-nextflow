package io.nextflow.intellij.hover

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import io.nextflow.intellij.lsp.isNextflowFile

class NextflowHoverDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (!isNextflowFile(file.name)) return emptyList()

        val text = file.text
        val html = NextflowHoverSupport.paramHover(file, text, offset)
            ?: NextflowHoverSupport.variableHover(text, offset)

        return if (html == null) {
            emptyList()
        } else {
            listOf(NextflowHoverDocumentationTarget(html))
        }
    }

}

private class NextflowHoverDocumentationTarget(private val html: String) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        // The target is immutable (plain HTML snapshot), so a strong-reference
        // pointer is safe. Implemented as a lambda instead of the experimental
        // Pointer.hardPointer() factory to avoid an extra experimental API usage.
        return Pointer { this }
    }

    override fun computePresentation(): TargetPresentation {
        return TargetPresentation.builder("Nextflow").presentation()
    }

    override fun computeDocumentation(): DocumentationResult {
        return DocumentationResult.documentation(html)
    }

    override fun computeDocumentationHint(): String {
        return html
    }
}
