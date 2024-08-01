package com.github.warningimhack3r.npmupdatedependencies.ui.quickfix

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.QuickFixesCommon
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class BlacklistVersionFix(
    private val index: Int,
    private val dependencyName: String,
    private val versionPattern: String,
    private val versionName: String? = null
) : BaseIntentionAction() {
    override fun getText() =
        "${Versions.Kind.entries.size + 1 + index}. Blacklist ${versionName ?: versionPattern} for $dependencyName"

    override fun getFamilyName() = "Blacklist version $versionPattern for $dependencyName"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
        QuickFixesCommon.getAvailability(editor, file)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        // Exclude pattern
        NUDSettingsState.instance.excludedVersions[dependencyName] =
            NUDSettingsState.instance.excludedVersions[dependencyName]
                ?.plus(versionPattern)?.distinct()
                ?: listOf(versionPattern)
        file?.let {
            DaemonCodeAnalyzer.getInstance(project).restart(it)
        }
        // Clear the cache
        NUDState.getInstance(project).availableUpdates.remove(dependencyName)
    }
}
