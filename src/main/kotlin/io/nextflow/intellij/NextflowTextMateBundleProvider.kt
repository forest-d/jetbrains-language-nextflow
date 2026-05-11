package io.nextflow.intellij

import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import java.nio.file.Path

class NextflowTextMateBundleProvider : TextMateBundleProvider {
    override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
        val bundlePath = Path.of(
            javaClass.getResource("/textmate/nextflow")?.toURI()
                ?: return emptyList()
        )
        return listOf(TextMateBundleProvider.PluginBundle("nextflow", bundlePath))
    }
}
