package io.nextflow.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.FileTypeFileViewProviders
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokensStructurelessFileViewProviderFactory
import io.nextflow.intellij.lsp.LanguageServerDownloader
import io.nextflow.intellij.lsp.NextflowRealtimeDiagnostics
import io.nextflow.intellij.settings.NextflowSettings

class NextflowStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        LOG.info("Nextflow plugin activated for project: ${project.name}")
        registerSemanticTokenProviders()
        NextflowRealtimeDiagnostics.install(project)

        val versionPrefix = NextflowSettings.getInstance().state.languageServerVersion.versionPrefix
        LanguageServerDownloader.ensureDownloaded(versionPrefix) { path ->
            if (path != null) {
                LOG.info("Language server ready: $path")
            } else {
                LOG.warn("Language server not available")
                NextflowNotifications.warn(
                    project,
                    "Nextflow language server is not available. Check your network connection or cached language server version."
                )
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(NextflowStartupActivity::class.java)
        private var semanticTokensRegistered = false

        /**
         * Register LSP4IJ's semantic token FileViewProvider for our custom file types.
         *
         * LSP4IJ's built-in registrar ([LSPSemanticTokensStructurelessFileViewProviderRegistrar])
         * only processes [com.intellij.openapi.fileTypes.impl.AbstractFileType] instances
         * (user-defined file types). Our plugin file types implement [com.intellij.openapi.fileTypes.FileType]
         * directly, so they are skipped. Without this registration, Nextflow files get plain-text
         * PSI with no semantic elements, which prevents hover (textDocument/hover), go-to-definition,
         * and other client-initiated LSP features from working.
         */
        @Synchronized
        private fun registerSemanticTokenProviders() {
            if (semanticTokensRegistered) return
            semanticTokensRegistered = true

            val factory = LSPSemanticTokensStructurelessFileViewProviderFactory()
            LOG.warn("Registering semantic token FileViewProviders...")
            LOG.warn("  Factory class: ${factory.javaClass.name}")
            LOG.warn("  NextflowFileType: ${NextflowFileType.INSTANCE.name} (${NextflowFileType.INSTANCE.javaClass.name})")
            LOG.warn("  NextflowTestFileType: ${NextflowTestFileType.INSTANCE.name} (${NextflowTestFileType.INSTANCE.javaClass.name})")
            LOG.warn("  NextflowConfigFileType: ${NextflowConfigFileType.INSTANCE.name} (${NextflowConfigFileType.INSTANCE.javaClass.name})")

            FileTypeFileViewProviders.INSTANCE.addExplicitExtension(NextflowFileType.INSTANCE, factory)
            FileTypeFileViewProviders.INSTANCE.addExplicitExtension(NextflowTestFileType.INSTANCE, factory)
            FileTypeFileViewProviders.INSTANCE.addExplicitExtension(NextflowConfigFileType.INSTANCE, factory)
            LOG.warn("Registered LSP semantic token FileViewProvider for Nextflow file types")

            // Verify registration took effect
            val nfProvider = FileTypeFileViewProviders.INSTANCE.forKey(NextflowFileType.INSTANCE)
            LOG.warn("  Verify NF provider after registration: ${nfProvider?.javaClass?.name ?: "NULL"}")
        }
    }
}
