package io.nextflow.intellij.lsp

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import io.nextflow.intellij.settings.ErrorReportingMode
import io.nextflow.intellij.settings.NextflowSettings
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.PublishDiagnosticsParams
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom language client that provides Nextflow settings to the language server
 * and filters diagnostics based on the user's error reporting mode.
 *
 * The Nextflow LS has an upstream bug where `didChangeConfiguration` cannot apply
 * settings at runtime (LSP4J deserializes the `settings` Object field as LinkedTreeMap,
 * but the LS's JsonUtils.getObjectPath() requires JsonObject). As a workaround, this
 * client filters published diagnostics on the IDE side based on the configured
 * [ErrorReportingMode].
 */
class NextflowLanguageClient(project: Project) : LanguageClientImpl(project) {
    private val publishedUris: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        synchronized(instances) {
            instances[project] = this
        }
    }

    override fun createSettings(): Any {
        return GSON.toJsonTree(NextflowSettings.getInstance().toFlatLspSettings()) as JsonObject
    }

    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
        publishedUris.add(params.uri)
        val mode = NextflowSettings.getInstance().state.errorReportingMode
        when (mode) {
            ErrorReportingMode.OFF -> params.diagnostics = emptyList()
            ErrorReportingMode.ERRORS -> params.diagnostics = params.diagnostics.filter { isError(it) }
            else -> {} // WARNINGS and PARANOID: pass through all diagnostics
        }
        super.publishDiagnostics(params)
    }

    /**
     * Clear all previously published diagnostics. Called when the error reporting
     * mode changes so existing squiggles are removed before fresh diagnostics
     * arrive through the filter.
     */
    fun clearDiagnostics() {
        for (uri in publishedUris) {
            super.publishDiagnostics(PublishDiagnosticsParams(uri, emptyList()))
        }
    }

    companion object {
        private val GSON = GsonBuilder().create()
        private val instances = WeakHashMap<Project, NextflowLanguageClient>()

        fun getInstance(project: Project): NextflowLanguageClient? = synchronized(instances) {
            instances[project]
        }

        private fun isError(diagnostic: Diagnostic): Boolean =
            diagnostic.severity == null || diagnostic.severity == DiagnosticSeverity.Error
    }
}
