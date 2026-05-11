package io.nextflow.intellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

enum class ErrorReportingMode(val displayName: String, val lspValue: String) {
    OFF("Off", "off"),
    ERRORS("Errors", "errors"),
    WARNINGS("Warnings", "warnings"),
    PARANOID("Paranoid", "paranoid");

    override fun toString(): String = displayName
}

enum class LanguageServerVersion(val displayName: String, val versionPrefix: String) {
    V26_04("26.04", "v26.04"),
    V25_10("25.10", "v25.10"),
    V25_04("25.04", "v25.04"),
    V24_10("24.10", "v24.10");

    override fun toString(): String = displayName
}

@Service(Service.Level.APP)
@State(name = "NextflowSettings", storages = [Storage("nextflow.xml")])
class NextflowSettings : PersistentStateComponent<NextflowSettings.State> {

    data class State(
        var completionMaxItems: Int = DEFAULT_COMPLETION_MAX_ITEMS,
        var completionExtended: Boolean = false,
        var debug: Boolean = false,
        var errorReportingMode: ErrorReportingMode = ErrorReportingMode.WARNINGS,
        var harshilAlignment: Boolean = false,
        var maheshForm: Boolean = false,
        var sortDeclarations: Boolean = false,
        var filesExclude: MutableList<String> = DEFAULT_EXCLUDES.toMutableList(),
        var languageServerVersion: LanguageServerVersion = LanguageServerVersion.V26_04,
        var javaHome: String = "",
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun toLspSettings(): Map<String, Any> {
        val excludes = state.filesExclude.map { it.trim() }.filter { it.isNotEmpty() }
        val nextflowSettings = mapOf(
            "completion" to mapOf(
                "extended" to state.completionExtended,
                "maxItems" to state.completionMaxItems,
            ),
            "debug" to state.debug,
            "errorReportingMode" to state.errorReportingMode.lspValue,
            "files" to mapOf("exclude" to excludes),
            "formatting" to mapOf(
                "harshilAlignment" to state.harshilAlignment,
                "maheshForm" to state.maheshForm,
                "sortDeclarations" to state.sortDeclarations,
            ),
            "java" to mapOf("home" to state.javaHome.ifBlank { null }),
            "languageVersion" to state.languageServerVersion.versionPrefix,
            "targetVersion" to state.languageServerVersion.versionPrefix,
            // The language server's native configuration shape uses these flat field names.
            "excludePatterns" to excludes,
            "extendedCompletion" to state.completionExtended,
            "harshilAlignment" to state.harshilAlignment,
            "maheshForm" to state.maheshForm,
            "maxCompletionItems" to state.completionMaxItems,
            "sortDeclarations" to state.sortDeclarations,
        )

        return mapOf("nextflow" to nextflowSettings)
    }

    companion object {
        const val DEFAULT_COMPLETION_MAX_ITEMS = 100
        val DEFAULT_EXCLUDES = listOf(".git", ".nf-test", "work")

        fun getInstance(): NextflowSettings =
            ApplicationManager.getApplication().getService(NextflowSettings::class.java)
    }
}
