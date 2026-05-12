package io.nextflow.intellij.lsp

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionFeature
import com.redhat.devtools.lsp4ij.features.completion.CompletionPrefix
import org.eclipse.lsp4j.CompletionItem

/**
 * Debug completion feature that logs what the LSP server returns.
 * Remove once completion is working correctly.
 */
class NextflowCompletionFeature : LSPCompletionFeature() {
    override fun addLookupItem(
        context: LSPCompletionContext,
        prefix: CompletionPrefix,
        result: CompletionResultSet,
        lookupItem: LookupElement,
        priority: Int,
        item: CompletionItem,
    ) {
        itemCount++
        if (itemCount <= 10) {
            LOG.warn("  COMPLETION ITEM [$itemCount]: label='${item.label}', kind=${item.kind}, detail='${item.detail?.take(60)}'")
        }
        super.addLookupItem(context, prefix, result, lookupItem, priority, item)
    }

    override fun isSupported(file: PsiFile): Boolean {
        val supported = super.isSupported(file)
        LOG.warn("=== LSP COMPLETION: isSupported(${file.name}) = $supported ===")
        return supported
    }

    companion object {
        private val LOG = Logger.getInstance(NextflowCompletionFeature::class.java)
        private var itemCount = 0

        fun resetCount() {
            itemCount = 0
        }
    }
}
