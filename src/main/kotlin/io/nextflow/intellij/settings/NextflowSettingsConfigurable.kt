package io.nextflow.intellij.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.SpinnerNumberModel

class NextflowSettingsConfigurable : SearchableConfigurable {
    private val settings = NextflowSettings.getInstance()

    private lateinit var panel: JPanel
    private lateinit var maxItems: JSpinner
    private lateinit var extendedCompletion: JBCheckBox
    private lateinit var debug: JBCheckBox
    private lateinit var errorReportingMode: JComboBox<ErrorReportingMode>
    private lateinit var harshilAlignment: JBCheckBox
    private lateinit var maheshForm: JBCheckBox
    private lateinit var sortDeclarations: JBCheckBox
    private lateinit var filesExclude: JTextArea
    private lateinit var languageServerVersion: JComboBox<LanguageServerVersion>
    private lateinit var javaHome: JBTextField

    override fun getId(): String = "preferences.nextflow"

    override fun getDisplayName(): String = "Nextflow"

    override fun createComponent(): JComponent {
        maxItems = JSpinner(SpinnerNumberModel(NextflowSettings.DEFAULT_COMPLETION_MAX_ITEMS, 1, 1000, 1))
        extendedCompletion = JBCheckBox("Include completions from outside the current script")
        debug = JBCheckBox("Enable debug logging and hover diagnostics")
        errorReportingMode = JComboBox(ErrorReportingMode.entries.toTypedArray())
        harshilAlignment = JBCheckBox("Use Harshil alignment")
        maheshForm = JBCheckBox("Place process outputs at the end when formatting")
        sortDeclarations = JBCheckBox("Sort declarations when formatting")
        filesExclude = JTextArea(4, 32)
        languageServerVersion = JComboBox(LanguageServerVersion.entries.toTypedArray())
        javaHome = JBTextField()

        panel = JPanel(BorderLayout())
        val form = JPanel(GridBagLayout())
        form.border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        var row = 0
        form.add(JBLabel("Language server version:"), labelConstraints(row))
        form.add(languageServerVersion, fieldConstraints(row++))

        form.add(JBLabel("Java home:"), labelConstraints(row))
        form.add(javaHome, fieldConstraints(row++))

        form.add(JBLabel("Completion max items:"), labelConstraints(row))
        form.add(maxItems, fieldConstraints(row++))

        form.add(JBLabel("Error reporting:"), labelConstraints(row))
        form.add(errorReportingMode, fieldConstraints(row++))

        form.add(JBLabel("Excluded folders:"), labelConstraints(row))
        form.add(JBScrollPane(filesExclude), fieldConstraints(row++))

        form.add(extendedCompletion, fullWidthConstraints(row++))
        form.add(debug, fullWidthConstraints(row++))
        form.add(harshilAlignment, fullWidthConstraints(row++))
        form.add(maheshForm, fullWidthConstraints(row++))
        form.add(sortDeclarations, fullWidthConstraints(row++))
        form.add(JPanel(), fillerConstraints(row))

        panel.add(form, BorderLayout.CENTER)
        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val state = settings.state
        return state.completionMaxItems != maxItems.value as Int ||
            state.completionExtended != extendedCompletion.isSelected ||
            state.debug != debug.isSelected ||
            state.errorReportingMode != errorReportingMode.selectedItem ||
            state.harshilAlignment != harshilAlignment.isSelected ||
            state.maheshForm != maheshForm.isSelected ||
            state.sortDeclarations != sortDeclarations.isSelected ||
            state.filesExclude != parseExcludes() ||
            state.languageServerVersion != languageServerVersion.selectedItem ||
            state.javaHome != javaHome.text.trim()
    }

    override fun apply() {
        val state = settings.state
        val oldVersion = state.languageServerVersion
        val oldJavaHome = state.javaHome
        val oldErrorMode = state.errorReportingMode

        state.completionMaxItems = maxItems.value as Int
        state.completionExtended = extendedCompletion.isSelected
        state.debug = debug.isSelected
        state.errorReportingMode = errorReportingMode.selectedItem as ErrorReportingMode
        state.harshilAlignment = harshilAlignment.isSelected
        state.maheshForm = maheshForm.isSelected
        state.sortDeclarations = sortDeclarations.isSelected
        state.filesExclude = parseExcludes()
        state.languageServerVersion = languageServerVersion.selectedItem as LanguageServerVersion
        state.javaHome = javaHome.text.trim()

        val restartRequired = oldVersion != state.languageServerVersion || oldJavaHome != state.javaHome
        val errorModeChanged = oldErrorMode != state.errorReportingMode
        ProjectManager.getInstance().openProjects.forEach { project ->
            NextflowLspConfigurationNotifier.notifyChanged(project, restartRequired, errorModeChanged)
        }
    }

    override fun reset() {
        val state = settings.state
        maxItems.value = state.completionMaxItems
        extendedCompletion.isSelected = state.completionExtended
        debug.isSelected = state.debug
        errorReportingMode.selectedItem = state.errorReportingMode
        harshilAlignment.isSelected = state.harshilAlignment
        maheshForm.isSelected = state.maheshForm
        sortDeclarations.isSelected = state.sortDeclarations
        filesExclude.text = state.filesExclude.joinToString("\n")
        languageServerVersion.selectedItem = state.languageServerVersion
        javaHome.text = state.javaHome
    }

    private fun parseExcludes(): MutableList<String> =
        filesExclude.text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toMutableList()

    private fun labelConstraints(row: Int) = GridBagConstraints().apply {
        gridx = 0
        gridy = row
        anchor = GridBagConstraints.WEST
        insets.set(0, 0, 8, 12)
    }

    private fun fieldConstraints(row: Int) = GridBagConstraints().apply {
        gridx = 1
        gridy = row
        weightx = 1.0
        fill = GridBagConstraints.HORIZONTAL
        insets.set(0, 0, 8, 0)
    }

    private fun fullWidthConstraints(row: Int) = GridBagConstraints().apply {
        gridx = 0
        gridy = row
        gridwidth = 2
        weightx = 1.0
        fill = GridBagConstraints.HORIZONTAL
        insets.set(0, 0, 8, 0)
    }

    private fun fillerConstraints(row: Int) = GridBagConstraints().apply {
        gridx = 0
        gridy = row
        gridwidth = 2
        weighty = 1.0
        fill = GridBagConstraints.BOTH
    }
}
