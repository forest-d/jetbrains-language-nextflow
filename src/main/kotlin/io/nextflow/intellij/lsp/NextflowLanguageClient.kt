package io.nextflow.intellij.lsp

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import io.nextflow.intellij.settings.NextflowSettings

/**
 * Custom language client that provides Nextflow settings to the language server.
 *
 * LSP4IJ calls [createSettings] for both `workspace/configuration` pull requests
 * and `workspace/didChangeConfiguration` push notifications. All other LSP
 * lifecycle (didOpen, didChange, didSave, workspace folders) is handled by LSP4IJ.
 */
class NextflowLanguageClient(project: Project) : LanguageClientImpl(project) {

    override fun createSettings(): Any {
        return GSON.toJsonTree(NextflowSettings.getInstance().toFlatLspSettings()) as JsonObject
    }

    companion object {
        private val GSON = GsonBuilder().create()
    }
}
