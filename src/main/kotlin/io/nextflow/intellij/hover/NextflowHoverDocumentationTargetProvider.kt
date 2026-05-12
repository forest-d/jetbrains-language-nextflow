package io.nextflow.intellij.hover

import com.intellij.model.Pointer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile

class NextflowHoverDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (!isNextflowFile(file.name)) return emptyList()

        val text = file.text
        val html = NextflowHoverSupport.paramHover(file, text, offset)
            ?: NextflowHoverSupport.variableHover(text, offset)

        LOG.warn(
            "Nextflow hover fallback targetProvider: file=${file.name}, offset=$offset, " +
                "matched=${html != null}, token='${tokenAt(text, offset)}'"
        )

        return if (html == null) {
            emptyList()
        } else {
            listOf(NextflowHoverDocumentationTarget(html))
        }
    }

    private fun isNextflowFile(name: String): Boolean {
        return name.endsWith(".nf") || name.endsWith(".nf.test") || name == "nextflow.config"
    }

    private fun tokenAt(text: String, offset: Int): String {
        if (text.isEmpty()) return ""
        val safeOffset = offset.coerceIn(0, text.length - 1)
        var start = safeOffset
        while (start > 0 && isTokenChar(text[start - 1])) start--
        var end = safeOffset
        while (end < text.length && isTokenChar(text[end])) end++
        return text.substring(start, end).take(80)
    }

    private fun isTokenChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_' || char == '.'
    }

    companion object {
        private val LOG = Logger.getInstance(NextflowHoverDocumentationTargetProvider::class.java)
    }
}

private class NextflowHoverDocumentationTarget(private val html: String) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        return Pointer.hardPointer(this)
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
