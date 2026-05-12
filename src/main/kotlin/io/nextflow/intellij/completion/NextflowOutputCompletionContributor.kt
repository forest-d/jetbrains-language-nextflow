package io.nextflow.intellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder

class NextflowOutputCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        if (!isNextflowFile(file.name)) return

        val text = parameters.editor.document.text
        val access = NextflowOutputCompletionSupport.outputAccessAt(text, parameters.offset) ?: return
        val outputs = NextflowOutputCompletionSupport.findOutputs(text, access.processName)
            .filter { it.startsWith(access.outputPrefix) }

        if (outputs.isEmpty()) return

        val prefixedResult = result.withPrefixMatcher(access.outputPrefix)
        outputs.forEach { output ->
            prefixedResult.addElement(
                LookupElementBuilder.create(output)
                    .withTypeText("${access.processName}.out", true)
            )
        }
        prefixedResult.stopHere()
    }

    private fun isNextflowFile(name: String): Boolean {
        return name.endsWith(".nf") || name.endsWith(".nf.test") || name == "nextflow.config"
    }
}
