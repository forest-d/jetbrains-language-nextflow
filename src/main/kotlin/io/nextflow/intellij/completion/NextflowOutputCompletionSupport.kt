package io.nextflow.intellij.completion

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import io.nextflow.intellij.hover.NextflowSchemaHoverParser

object NextflowOutputCompletionSupport {
    private val outputAccessRegex = Regex("""(?:^|[^\w])([A-Za-z_][A-Za-z0-9_]*)\.out\.([A-Za-z_][A-Za-z0-9_]*)?$""")
    private val paramsAccessRegex = Regex("""(?:^|[^\w])params\.([A-Za-z_][A-Za-z0-9_]*)?$""")
    private val channelAccessRegex = Regex("""(?:^|[^\w])Channel\.([A-Za-z_][A-Za-z0-9_]*)?$""")
    private val processHeaderRegex = Regex("""\bprocess\s+([A-Za-z_][A-Za-z0-9_]*)\s*\{""")
    private val includeEntryRegex = Regex("""\binclude\s*\{\s*([^}]+)\s*}\s*from\b""")
    private val identifierRegex = Regex("""[A-Za-z_][A-Za-z0-9_]*$""")
    private val configParamRegex = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=""")
    private val emitRegex = Regex("""\bemit\s*:\s*([A-Za-z_][A-Za-z0-9_]*)""")
    private val sectionRegex = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(?:$|//.*$)""")

    val channelFactories = listOf(
        "of",
        "fromPath",
        "fromFilePairs",
        "fromList",
        "fromSRA",
        "empty",
        "value",
        "watchPath",
        "topic",
    )

    fun outputAccessAt(text: String, offset: Int): OutputAccess? {
        if (offset < 0 || offset > text.length) return null

        val prefix = linePrefix(text, offset)
        val match = outputAccessRegex.find(prefix) ?: return null

        return OutputAccess(
            processName = match.groupValues[1],
            outputPrefix = match.groups[2]?.value.orEmpty(),
        )
    }

    fun findOutputs(text: String, processName: String): List<String> {
        val body = findProcessBody(text, processName) ?: return emptyList()
        val outputBlock = findOutputBlock(body) ?: return emptyList()

        return emitRegex.findAll(outputBlock)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    fun paramsAccessAt(text: String, offset: Int): MemberAccess? {
        val match = paramsAccessRegex.find(linePrefix(text, offset)) ?: return null
        return MemberAccess(match.groups[1]?.value.orEmpty())
    }

    fun channelAccessAt(text: String, offset: Int): MemberAccess? {
        val match = channelAccessRegex.find(linePrefix(text, offset)) ?: return null
        return MemberAccess(match.groups[1]?.value.orEmpty())
    }

    fun processPrefixAt(text: String, offset: Int): String? {
        val prefix = linePrefix(text, offset)
        if (prefix.contains(".")) return null
        if (prefix.substringBeforeLast(" ", "").trimStart().let { it.endsWith("include") || it.endsWith("process") }) return null
        return identifierRegex.find(prefix)?.value
    }

    fun findProcessNames(text: String): List<String> {
        val localProcesses = processHeaderRegex.findAll(text).map { it.groupValues[1] }
        val includedEntries = includeEntryRegex.findAll(text).flatMap { match ->
            match.groupValues[1]
                .split(";")
                .asSequence()
                .map { entry -> entry.substringBefore(" as ").trim() }
                .filter { it.matches(Regex("""[A-Za-z_][A-Za-z0-9_]*""")) }
        }
        return (localProcesses + includedEntries).distinct().toList()
    }

    fun findParamNames(file: PsiFile): List<String> {
        val schemaParams = findSchemaParamNames(file)
        val configParams = findConfigParamNames(file)
        return (schemaParams + configParams).distinct().toList()
    }

    private fun findSchemaParamNames(file: PsiFile): List<String> {
        val schemaFile = findSiblingOrAncestorFile(file, "nextflow_schema.json") ?: return emptyList()
        val json = runCatching { VfsUtilCore.loadText(schemaFile) }.getOrNull() ?: return emptyList()
        return NextflowSchemaHoverParser.findParamNames(json)
    }

    private fun findConfigParamNames(file: PsiFile): List<String> {
        val configFile = findSiblingOrAncestorFile(file, "nextflow.config") ?: return emptyList()
        val text = runCatching { VfsUtilCore.loadText(configFile) }.getOrNull() ?: return emptyList()
        val paramsBlock = text.substringAfter("params {", missingDelimiterValue = "")
            .substringBefore("\n}", missingDelimiterValue = "")
        if (paramsBlock.isBlank()) return emptyList()

        return paramsBlock.lines()
            .mapNotNull { configParamRegex.matchEntire(it)?.groupValues?.get(1) }
            .distinct()
    }

    private fun findProcessBody(text: String, processName: String): String? {
        for (match in processHeaderRegex.findAll(text)) {
            if (match.groupValues[1] != processName) continue

            val bodyStart = match.range.last + 1
            val bodyEnd = findMatchingBrace(text, bodyStart - 1) ?: return null
            return text.substring(bodyStart, bodyEnd)
        }
        return null
    }

    private fun findMatchingBrace(text: String, openBraceOffset: Int): Int? {
        var depth = 0
        var index = openBraceOffset
        while (index < text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return null
    }

    private fun findOutputBlock(processBody: String): String? {
        val lines = processBody.lines()
        val startIndex = lines.indexOfFirst { it.trim() == "output:" }
        if (startIndex == -1) return null

        val block = StringBuilder()
        for (index in startIndex + 1 until lines.size) {
            val line = lines[index]
            val section = sectionRegex.matchEntire(line)
            if (section != null && section.groupValues[1] != "emit") break
            block.appendLine(line)
        }
        return block.toString()
    }

    private fun findSiblingOrAncestorFile(file: PsiFile, name: String): com.intellij.openapi.vfs.VirtualFile? {
        var dir = file.virtualFile?.parent
        while (dir != null) {
            dir.findChild(name)?.let { return it }
            dir = dir.parent
        }
        return file.project.basePath
            ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
            ?.findChild(name)
    }

    private fun linePrefix(text: String, offset: Int): String {
        val safeOffset = offset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (safeOffset - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        return text.substring(lineStart, safeOffset)
    }

    data class OutputAccess(val processName: String, val outputPrefix: String)
    data class MemberAccess(val prefix: String)
}
