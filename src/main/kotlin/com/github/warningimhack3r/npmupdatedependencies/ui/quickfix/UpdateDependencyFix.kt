package com.github.warningimhack3r.npmupdatedependencies.ui.quickfix

import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.stringValue
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Versions
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.NUDHelper
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.QuickFixesCommon
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class UpdateDependencyFix(
    private val versions: Versions,
    private val kind: Versions.Kind,
    private val property: JsonProperty
) : BaseIntentionAction() {
    private val log = logger<UpdateDependencyFix>()
    private val version = versions.from(kind)

    override fun getText() = QuickFixesCommon.getPositionPrefix(
        kind,
        versions.orderedAvailableKinds(NUDSettingsState.instance.defaultUpdateType!!)
    ) + "Update to ${kind.toString().lowercase()} version ($version)"

    override fun getFamilyName() = "Update dependency"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        QuickFixesCommon.getAvailability(editor, file)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file == null) {
            log.warn("Trying to update dependency but file is null")
            return
        }
        val prefix = NUDHelper.Regex.semverPrefix.find(property.value?.stringValue() ?: "")?.value ?: ""
        val newElement = NUDHelper.createElement(project, "\"$prefix$version\"", "JSON")
        NUDHelper.safeFileWrite(file, "Update \"${property.name}\" to $version") {
            property.value?.replace(newElement)
        }
    }
}
