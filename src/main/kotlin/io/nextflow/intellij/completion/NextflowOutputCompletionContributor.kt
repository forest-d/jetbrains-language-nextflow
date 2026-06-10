package io.nextflow.intellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import io.nextflow.intellij.lsp.isNextflowFile

class NextflowOutputCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        if (!isNextflowFile(file.name)) return

        val text = parameters.editor.document.text
        addProcessOutputCompletions(text, parameters, result)
            ?: addParamCompletions(text, parameters, result)
            ?: addChannelCompletions(text, parameters, result)
            ?: addProcessNameCompletions(text, parameters, result)
    }

    private fun addProcessOutputCompletions(
        text: String,
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ): Unit? {
        val access = NextflowOutputCompletionSupport.outputAccessAt(text, parameters.offset) ?: return null
        val outputs = NextflowOutputCompletionSupport.findOutputs(text, access.processName)
            .filter { it.startsWith(access.outputPrefix) }

        if (outputs.isEmpty()) return null

        addItems(result, access.outputPrefix, outputs, "${access.processName}.out")
        return Unit
    }

    private fun addParamCompletions(
        text: String,
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ): Unit? {
        val access = NextflowOutputCompletionSupport.paramsAccessAt(text, parameters.offset) ?: return null
        val params = NextflowOutputCompletionSupport.findParamNames(parameters.originalFile)
            .filter { it.startsWith(access.prefix) }

        if (params.isEmpty()) return null

        addItems(result, access.prefix, params, "params")
        return Unit
    }

    private fun addChannelCompletions(
        text: String,
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ): Unit? {
        val access = NextflowOutputCompletionSupport.channelAccessAt(text, parameters.offset) ?: return null
        val factories = NextflowOutputCompletionSupport.channelFactories
            .filter { it.startsWith(access.prefix) }

        if (factories.isEmpty()) return null

        addItems(result, access.prefix, factories, "Channel")
        return Unit
    }

    private fun addProcessNameCompletions(
        text: String,
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ): Unit? {
        val prefix = NextflowOutputCompletionSupport.processPrefixAt(text, parameters.offset) ?: return null
        val processes = NextflowOutputCompletionSupport.findProcessNames(text)
            .filter { it.startsWith(prefix) }
        val workflows = NextflowOutputCompletionSupport.findWorkflowNames(text)
            .filter { it.startsWith(prefix) && it !in processes }

        if (processes.isEmpty() && workflows.isEmpty()) return null

        addTypedItems(result, prefix, processes.map { it to "process" } + workflows.map { it to "workflow" })
        return Unit
    }

    private fun addItems(result: CompletionResultSet, prefix: String, labels: List<String>, typeText: String) {
        addTypedItems(result, prefix, labels.map { it to typeText })
    }

    private fun addTypedItems(result: CompletionResultSet, prefix: String, items: List<Pair<String, String>>) {
        val prefixedResult = result.withPrefixMatcher(prefix)
        items.forEach { (label, typeText) ->
            prefixedResult.addElement(
                LookupElementBuilder.create(label)
                    .withTypeText(typeText, true)
            )
        }
        prefixedResult.stopHere()
    }
}
