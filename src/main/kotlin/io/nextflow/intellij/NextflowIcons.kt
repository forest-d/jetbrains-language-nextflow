package io.nextflow.intellij

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object NextflowIcons {
    @JvmField val NEXTFLOW: Icon = IconLoader.getIcon("/icons/nextflow.svg", NextflowIcons::class.java)
    @JvmField val NEXTFLOW_CONFIG: Icon = IconLoader.getIcon("/icons/nextflow-config.svg", NextflowIcons::class.java)
    @JvmField val NEXTFLOW_TEST: Icon = IconLoader.getIcon("/icons/nextflow-test.svg", NextflowIcons::class.java)
}
