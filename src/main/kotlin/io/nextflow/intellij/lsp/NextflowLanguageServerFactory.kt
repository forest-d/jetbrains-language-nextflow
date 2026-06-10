package io.nextflow.intellij.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.client.features.LSPConfigurationFeature
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider

class NextflowLanguageServerFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        return NextflowLanguageServer(project)
    }

    override fun createLanguageClient(project: Project): LanguageClientImpl {
        return NextflowLanguageClient(project)
    }

    // LSPClientFeatures is @ApiStatus.Experimental in LSP4IJ (still experimental on
    // upstream main as of 2026-06). There is no stable alternative: this override is
    // the only way to make LSP4IJ push workspace/didChangeConfiguration when settings
    // change, which the settings sync (diagnostic mode, error reporting) depends on.
    // The resulting plugin-verifier experimental-API warnings are unavoidable.
    override fun createClientFeatures(): LSPClientFeatures {
        return LSPClientFeatures()
            .setConfigurationFeature(
                LSPConfigurationFeature().apply {
                    onConfigurationChanged = LSPConfigurationFeature.OnConfigurationChanged.CALL_DID_CHANGE_CONFIGURATION
                }
            )
    }
}
