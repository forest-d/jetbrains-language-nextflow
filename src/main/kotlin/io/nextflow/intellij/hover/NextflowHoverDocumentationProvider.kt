package io.nextflow.intellij.hover

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

class NextflowHoverDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val anchor = originalElement ?: element ?: return null
        val file = anchor.containingFile ?: return null
        if (!isNextflowFile(file.name)) return null

        val offset = anchor.textRange?.startOffset ?: return null
        val text = file.text

        val html = NextflowHoverSupport.paramHover(file, text, offset)
            ?: NextflowHoverSupport.variableHover(text, offset)
        LOG.warn("Nextflow hover fallback legacy provider: file=${file.name}, offset=$offset, matched=${html != null}")
        return html
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        return generateDoc(element, originalElement)
    }

    private fun isNextflowFile(name: String): Boolean {
        return name.endsWith(".nf") || name.endsWith(".nf.test") || name == "nextflow.config"
    }

    companion object {
        private val LOG = Logger.getInstance(NextflowHoverDocumentationProvider::class.java)
    }
}

object NextflowHoverSupport {
    private val paramAccessRegex = Regex("""\bparams\.([A-Za-z_][A-Za-z0-9_]*)\b""")
    private val identifierRegex = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
    private val configParamRegex = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+?)\s*(?://.*)?$""")
    private val workflowInputRegex = Regex("""^\s*%s\s*(?://\s*(.+))?$""")
    private val assignmentRegex = Regex("""^\s*%s\s*=\s*(.+)$""")

    fun paramHover(file: PsiFile, text: String, offset: Int): String? {
        val access = findParamAccess(text, offset) ?: return null
        val schemaParam = findSchemaParam(file, access.name)
        val configValue = findConfigParam(file, access.name)

        if (schemaParam == null && configValue == null) return null

        return buildString {
            append("<b>params.")
            append(escape(access.name))
            append("</b>")

            if (schemaParam?.type != null) {
                append("<br/><code>")
                append(escape(schemaParam.type))
                append("</code>")
            }
            if (schemaParam?.description != null) {
                append("<p>")
                append(escape(schemaParam.description))
                append("</p>")
            }

            val defaultValue = schemaParam?.defaultValue ?: configValue
            if (defaultValue != null) {
                append("<p><b>Default:</b> <code>")
                append(escape(defaultValue))
                append("</code></p>")
            }
            if (!schemaParam?.enumValues.isNullOrEmpty()) {
                append("<p><b>Allowed values:</b> ")
                append(schemaParam.enumValues.joinToString(", ") { "<code>${escape(it)}</code>" })
                append("</p>")
            }
        }
    }

    fun variableHover(text: String, offset: Int): String? {
        val identifier = identifierAt(text, offset) ?: return null
        if (!identifier.name.endsWith("_ch")) return null

        return findVariableHover(text, offset, identifier.name)
    }

    private fun findParamAccess(text: String, offset: Int): ParamAccess? {
        val line = lineAt(text, offset)
        for (match in paramAccessRegex.findAll(line.text)) {
            val range = (line.start + match.range.first)..(line.start + match.range.last)
            if (offset !in range) continue
            return ParamAccess(match.groupValues[1])
        }
        return null
    }

    private fun identifierAt(text: String, offset: Int): Identifier? {
        val line = lineAt(text, offset)
        for (match in identifierRegex.findAll(line.text)) {
            val range = (line.start + match.range.first)..(line.start + match.range.last)
            if (offset in range) return Identifier(match.value)
        }
        return null
    }

    private fun findVariableHover(text: String, offset: Int, name: String): String? {
        val inputRegex = Regex(workflowInputRegex.pattern.format(Regex.escape(name)))
        val assignmentRegex = Regex(assignmentRegex.pattern.format(Regex.escape(name)))
        val lines = text.lines()
        var lineStart = 0
        var best: VariableHover? = null

        for ((index, line) in lines.withIndex()) {
            if (lineStart > offset) break

            inputRegex.matchEntire(line)?.let { match ->
                best = VariableHover(buildWorkflowInputHover(name, match.groups[1]?.value?.trim()?.takeIf { it.isNotEmpty() }))
            }

            assignmentRegex.matchEntire(line)?.let { match ->
                best = VariableHover(buildAssignmentHover(name, collectExpression(match.groupValues[1], lines.drop(index + 1))))
            }

            lineStart += line.length + 1
        }

        return best?.html
    }

    private fun buildWorkflowInputHover(name: String, typeHint: String?): String {
        return buildString {
            append("<b>")
            append(escape(name))
            append("</b>")
            append("<p>Workflow input channel</p>")
            if (typeHint != null) {
                append("<p><b>Hint:</b> <code>")
                append(escape(typeHint))
                append("</code></p>")
            }
        }
    }

    private fun buildAssignmentHover(name: String, expression: String): String {
        return buildString {
            append("<b>")
            append(escape(name))
            append("</b>")
            append("<p>Workflow channel</p>")
            append("<pre>")
            append(escape(expression))
            append("</pre>")
        }
    }

    private fun collectExpression(firstLine: String, followingLines: List<String>): String {
        val result = mutableListOf(firstLine.trimEnd())
        for (line in followingLines) {
            val trimmed = line.trimStart()
            if (!trimmed.startsWith(".")) break
            result += trimmed.trimEnd()
        }
        return result.joinToString("\n")
    }

    private fun findSchemaParam(file: PsiFile, name: String): SchemaParam? {
        val schemaFile = findSiblingOrAncestorFile(file, "nextflow_schema.json") ?: return null
        val json = runCatching { VfsUtilCore.loadText(schemaFile) }.getOrNull() ?: return null
        return NextflowSchemaHoverParser.findParam(json, name)
    }

    private fun findConfigParam(file: PsiFile, name: String): String? {
        val configFile = findSiblingOrAncestorFile(file, "nextflow.config") ?: return null
        val text = runCatching { VfsUtilCore.loadText(configFile) }.getOrNull() ?: return null
        val paramsBlock = text.substringAfter("params {", missingDelimiterValue = "")
            .substringBefore("\n}", missingDelimiterValue = "")
        if (paramsBlock.isBlank()) return null

        return paramsBlock.lines()
            .mapNotNull { configParamRegex.matchEntire(it) }
            .firstOrNull { it.groupValues[1] == name }
            ?.groupValues
            ?.get(2)
            ?.trim()
    }

    private fun findSiblingOrAncestorFile(file: PsiFile, name: String): com.intellij.openapi.vfs.VirtualFile? {
        var dir = file.virtualFile?.parent
        while (dir != null) {
            dir.findChild(name)?.let { return it }
            dir = dir.parent
        }

        val projectBase = projectBaseDir(file.project) ?: return null
        return projectBase.findChild(name)
    }

    private fun projectBaseDir(project: Project): com.intellij.openapi.vfs.VirtualFile? {
        return project.basePath?.let { path ->
            val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path)
            virtualFile?.takeIf { PsiManager.getInstance(project).findDirectory(it) != null }
        }
    }

    private fun lineAt(text: String, offset: Int): Line {
        val safeOffset = offset.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', (safeOffset - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val end = text.indexOf('\n', safeOffset).let { if (it == -1) text.length else it }
        return Line(start, text.substring(start, end))
    }

    private fun escape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private data class Line(val start: Int, val text: String)
    private data class ParamAccess(val name: String)
    private data class Identifier(val name: String)
    private data class VariableHover(val html: String)
}

data class SchemaParam(
    val type: String?,
    val description: String?,
    val defaultValue: String?,
    val enumValues: List<String>,
)
