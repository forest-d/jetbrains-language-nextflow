package io.nextflow.intellij.completion

object NextflowOutputCompletionSupport {
    private val outputAccessRegex = Regex("""(?:^|[^\w])([A-Za-z_][A-Za-z0-9_]*)\.out\.([A-Za-z_][A-Za-z0-9_]*)?$""")
    private val processHeaderRegex = Regex("""\bprocess\s+([A-Za-z_][A-Za-z0-9_]*)\s*\{""")
    private val emitRegex = Regex("""\bemit\s*:\s*([A-Za-z_][A-Za-z0-9_]*)""")
    private val sectionRegex = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(?:$|//.*$)""")

    fun outputAccessAt(text: String, offset: Int): OutputAccess? {
        if (offset < 0 || offset > text.length) return null

        val lineStart = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val prefix = text.substring(lineStart, offset)
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

    data class OutputAccess(val processName: String, val outputPrefix: String)
}
