package io.nextflow.intellij.navigation

import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.Processor
import io.nextflow.intellij.lsp.isNextflowFile
import io.nextflow.intellij.lsp.isNextflowPath

class NextflowUsageSearcher : CustomUsageSearcher() {
    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in Usage>,
        options: FindUsagesOptions,
    ) {
        val context = NextflowUsageSupport.usageContext(element)
        if (context == null) {
            LOG.debug(
                "NEXTFLOW_FIND_USAGES no-context " +
                    "element=${element.javaClass.name} " +
                    "range=${element.textRange?.let { "${it.startOffset}..${it.endOffset}" }} " +
                    "file=${element.containingFile?.virtualFile?.path ?: element.containingFile?.name}"
            )
            return
        }

        LOG.debug(
            "NEXTFLOW_FIND_USAGES start " +
                "symbol='${context.symbolName}' " +
                "origin=${context.file.path}:${context.offset} " +
                "element=${element.javaClass.name} " +
                "searchScope=${options.searchScope}"
        )

        val result = NextflowUsageSupport.findUsages(element.project, context.symbolName, context.file, context.offset)
        LOG.debug(
            "NEXTFLOW_FIND_USAGES resolved " +
                "symbol='${context.symbolName}' " +
                "mode=${result.mode} " +
                "filesScanned=${result.filesScanned} " +
                "usages=${result.locations.size} " +
                "sample=${result.locations.take(5).joinToString { "${it.file.name}:${it.offset}" }}"
        )

        for (usage in result.locations) {
            val usageInfo = ApplicationManager.getApplication().runReadAction<UsageInfo?> {
                val psiFile = PsiManager.getInstance(element.project).findFile(usage.file)
                    ?: return@runReadAction null
                UsageInfo(
                    psiFile,
                    usage.offset,
                    usage.offset + context.symbolName.length,
                    false,
                )
            }
            if (usageInfo == null) {
                LOG.debug("NEXTFLOW_FIND_USAGES missing-psi file=${usage.file.path} offset=${usage.offset}")
                continue
            }

            if (!processor.process(UsageInfo2UsageAdapter(usageInfo))) {
                LOG.debug(
                    "NEXTFLOW_FIND_USAGES processor-stopped " +
                        "symbol='${context.symbolName}' file=${usage.file.path} offset=${usage.offset}"
                )
                return
            }
        }

        LOG.debug("NEXTFLOW_FIND_USAGES done symbol='${context.symbolName}' emitted=${result.locations.size}")
    }

    companion object {
        private val LOG = Logger.getInstance(NextflowUsageSearcher::class.java)
    }
}

object NextflowUsageSupport {
    private val LOG = Logger.getInstance(NextflowUsageSupport::class.java)
    private val identifierRegex = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
    private val blockDeclarationRegex = Regex("""\bprocess\s+[A-Za-z_][A-Za-z0-9_]*\s*\{|\bworkflow(?:\s+[A-Za-z_][A-Za-z0-9_]*)?\s*\{|\bdef\s+[A-Za-z_][A-Za-z0-9_]*\s*\([^)]*\)\s*\{""")
    private const val PROCESS_DECLARATION_PATTERN = """\bprocess\s+%s\b"""
    private const val FUNCTION_DECLARATION_PATTERN = """\bdef\s+%s\s*\("""

    fun usageContext(element: PsiElement): UsageContext? {
        val file = element.containingFile ?: return null
        if (!isNextflowFile(file.name)) return null
        val virtualFile = file.virtualFile ?: return null
        val text = file.text
        val range = element.textRange ?: return null

        if (range.startOffset == 0 && range.endOffset >= text.length) {
            editorContext(element.project, virtualFile, text)?.let { return it }
        }

        val offsets = sequenceOf(range.startOffset, range.endOffset, range.startOffset - 1, range.endOffset - 1)
            .map { it.coerceIn(0, text.length) }
            .distinct()

        for (offset in offsets) {
            val token = identifierAt(text, offset) ?: continue
            return UsageContext(virtualFile, token.offset, token.name)
        }
        return null
    }

    private fun editorContext(project: Project, virtualFile: VirtualFile, text: String): UsageContext? {
        return ApplicationManager.getApplication().runReadAction<UsageContext?> {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@runReadAction null
            val editorFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return@runReadAction null
            if (editorFile != virtualFile) {
                LOG.debug(
                    "NEXTFLOW_FIND_USAGES editor-context-file-mismatch " +
                        "psiFile=${virtualFile.path} editorFile=${editorFile.path}"
                )
                return@runReadAction null
            }

            val caretOffset = editor.caretModel.offset.coerceIn(0, text.length)
            val token = identifierAt(text, caretOffset) ?: return@runReadAction null
            val context = UsageContext(virtualFile, token.offset, token.name)
            LOG.debug(
                "NEXTFLOW_FIND_USAGES editor-context " +
                    "symbol='${context.symbolName}' file=${virtualFile.path} caret=$caretOffset tokenOffset=${context.offset}"
            )
            context
        }
    }

    fun identifierAt(text: String, offset: Int): Token? {
        if (text.isEmpty()) return null
        val safeOffset = offset.coerceIn(0, text.length)
        val lookupOffset = when {
            safeOffset < text.length && isIdentifierPart(text[safeOffset]) -> safeOffset
            safeOffset > 0 && isIdentifierPart(text[safeOffset - 1]) -> safeOffset - 1
            else -> return null
        }

        val lineStart = text.lastIndexOf('\n', lookupOffset).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', lookupOffset).let { if (it == -1) text.length else it }
        if (lineStart > lineEnd) return null

        val line = text.substring(lineStart, lineEnd)
        for (match in identifierRegex.findAll(line)) {
            val range = (lineStart + match.range.first)..(lineStart + match.range.last)
            if (lookupOffset in range) return Token(match.value, range.first)
        }
        return null
    }

    fun findUsages(
        project: Project,
        symbolName: String,
        originFile: VirtualFile,
        originOffset: Int,
    ): UsageSearchResult {
        val files = nextflowFiles(project)
        // Load each file once; the text is reused for the global-declaration check and the scan.
        val fileTexts = files.mapNotNull { file ->
            runCatching { VfsUtilCore.loadText(file) }.getOrNull()?.let { file to it }
        }
        val isGlobal = fileTexts.any { (_, text) -> isDeclaredProcessOrFunction(text, symbolName) }

        if (!isGlobal) {
            val text = fileTexts.firstOrNull { (file, _) -> file == originFile }?.second
                ?: runCatching { VfsUtilCore.loadText(originFile) }.getOrNull()
                ?: return UsageSearchResult("local-read-failed", files.size, emptyList())
            val scope = containingBlockRange(text, originOffset) ?: text.indices
            return UsageSearchResult(
                mode = "local-block",
                filesScanned = 1,
                locations = findReferenceOffsets(text, symbolName, scope)
                    .map { UsageLocation(originFile, it) },
            )
        }

        return UsageSearchResult(
            mode = "project-global",
            filesScanned = files.size,
            locations = fileTexts.flatMap { (file, text) ->
                findReferenceOffsets(text, symbolName)
                    .map { UsageLocation(file, it) }
            },
        )
    }

    fun findReferenceOffsets(text: String, symbolName: String): List<Int> {
        // Collect declaration ranges once instead of re-scanning the whole text per occurrence.
        val declarationRanges =
            declarationRegex(PROCESS_DECLARATION_PATTERN, symbolName).findAll(text).map { it.range }.toList() +
                declarationRegex(FUNCTION_DECLARATION_PATTERN, symbolName).findAll(text).map { it.range }
        return findWholeWordOccurrences(text, symbolName)
            .filterNot { offset -> declarationRanges.any { offset in it } }
    }

    fun findReferenceOffsets(text: String, symbolName: String, scope: IntRange): List<Int> {
        return findReferenceOffsets(text, symbolName)
            .filter { it in scope }
    }

    fun findWholeWordOccurrences(text: String, symbolName: String): List<Int> {
        return Regex("""\b${Regex.escape(symbolName)}\b""")
            .findAll(text)
            .map { it.range.first }
            .toList()
    }

    private fun isIdentifierPart(char: Char): Boolean {
        return char == '_' || char.isLetterOrDigit()
    }

    private fun declarationRegex(pattern: String, symbolName: String): Regex {
        return Regex(pattern.format(Regex.escape(symbolName)))
    }

    private fun isDeclaredProcessOrFunction(text: String, symbolName: String): Boolean {
        return declarationRegex(PROCESS_DECLARATION_PATTERN, symbolName).containsMatchIn(text) ||
            declarationRegex(FUNCTION_DECLARATION_PATTERN, symbolName).containsMatchIn(text)
    }

    private fun containingBlockRange(text: String, offset: Int): IntRange? {
        return blockDeclarationRegex.findAll(text)
            .mapNotNull { match ->
                val openBrace = match.range.last
                val closeBrace = matchingCloseBrace(text, openBrace) ?: return@mapNotNull null
                if (offset in openBrace..closeBrace) openBrace..closeBrace else null
            }
            .lastOrNull()
    }

    private fun matchingCloseBrace(text: String, openBrace: Int): Int? {
        var depth = 0
        for (index in openBrace until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun nextflowFiles(project: Project): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val index = ProjectFileIndex.getInstance(project)
        index.iterateContent { file ->
            if (!file.isDirectory && file.path.isNextflowPath()) files.add(file)
            true
        }
        return files
    }

    data class Token(val name: String, val offset: Int)
    data class UsageContext(val file: VirtualFile, val offset: Int, val symbolName: String)
    data class UsageLocation(val file: VirtualFile, val offset: Int)
    data class UsageSearchResult(val mode: String, val filesScanned: Int, val locations: List<UsageLocation>)
}
