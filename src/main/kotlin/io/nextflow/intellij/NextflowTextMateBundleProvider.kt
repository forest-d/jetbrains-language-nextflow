package io.nextflow.intellij

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class NextflowTextMateBundleProvider : TextMateBundleProvider {
    override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
        val bundlePath = runCatching { resolveBundlePath() }
            .onFailure { LOG.warn("Unable to resolve bundled Nextflow TextMate grammar", it) }
            .getOrNull()
            ?: return emptyList()

        return listOf(TextMateBundleProvider.PluginBundle("nextflow", bundlePath))
    }

    private fun resolveBundlePath(): Path? {
        cachedBundlePath?.let { return it }

        return synchronized(cacheLock) {
            cachedBundlePath?.let { return@synchronized it }

            val resource = javaClass.getResource(BUNDLE_RESOURCE) ?: return@synchronized null
            val path = if (resource.protocol == "file") {
                Path.of(resource.toURI())
            } else {
                extractBundle()
            }
            cachedBundlePath = path
            path
        }
    }

    private fun extractBundle(): Path {
        val targetRoot = PathManager.getSystemDir().resolve("nextflow").resolve("textmate").resolve("nextflow")

        BUNDLE_FILES.forEach { relativePath ->
            val target = targetRoot.resolve(relativePath)
            Files.createDirectories(target.parent)

            javaClass.getResourceAsStream("$BUNDLE_RESOURCE/$relativePath").use { input ->
                requireNotNull(input) { "Missing TextMate bundle resource: $relativePath" }
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        return targetRoot
    }

    companion object {
        private val LOG = Logger.getInstance(NextflowTextMateBundleProvider::class.java)
        private const val BUNDLE_RESOURCE = "/textmate/nextflow"
        private val BUNDLE_FILES = listOf(
            "language-configuration.json",
            "package.json",
            "syntaxes/groovy.tmLanguage.json",
            "syntaxes/nextflow-config.tmLanguage.json",
            "syntaxes/nextflow.tmLanguage.json",
        )

        @Volatile
        private var cachedBundlePath: Path? = null

        private val cacheLock = Any()
    }
}
