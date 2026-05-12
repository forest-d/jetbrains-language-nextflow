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

    override fun createClientFeatures(): LSPClientFeatures {
        return LSPClientFeatures()
            .setConfigurationFeature(
                LSPConfigurationFeature().apply {
                    onConfigurationChanged = LSPConfigurationFeature.OnConfigurationChanged.CALL_DID_CHANGE_CONFIGURATION
                }
            )
    }
}
