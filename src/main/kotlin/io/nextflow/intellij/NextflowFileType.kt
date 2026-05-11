package io.nextflow.intellij

import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.plugins.textmate.TextMateBackedFileType
import javax.swing.Icon

class NextflowFileType private constructor() : FileType, TextMateBackedFileType {
    override fun getName(): String = "Nextflow"
    override fun getDescription(): String = "Nextflow script"
    override fun getDefaultExtension(): String = "nf"
    override fun getIcon(): Icon = NextflowIcons.NEXTFLOW
    override fun isBinary(): Boolean = false

    companion object {
        @JvmField val INSTANCE = NextflowFileType()
    }
}

class NextflowTestFileType private constructor() : FileType, TextMateBackedFileType {
    override fun getName(): String = "Nextflow Test"
    override fun getDescription(): String = "Nextflow test script"
    override fun getDefaultExtension(): String = "nf.test"
    override fun getIcon(): Icon = NextflowIcons.NEXTFLOW_TEST
    override fun isBinary(): Boolean = false

    companion object {
        @JvmField val INSTANCE = NextflowTestFileType()
    }
}

class NextflowConfigFileType private constructor() : FileType, TextMateBackedFileType {
    override fun getName(): String = "Nextflow Config"
    override fun getDescription(): String = "Nextflow configuration"
    override fun getDefaultExtension(): String = "config"
    override fun getIcon(): Icon = NextflowIcons.NEXTFLOW_CONFIG
    override fun isBinary(): Boolean = false

    companion object {
        @JvmField val INSTANCE = NextflowConfigFileType()
    }
}
