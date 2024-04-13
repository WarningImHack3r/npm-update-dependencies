package com.github.warningimhack3r.npmupdatedependencies.ui.quickfix

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.stringValue
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.NUDHelper
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.QuickFixesCommon
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.rd.util.printlnError

class UpdateDependencyFix(
    private val kind: Versions.Kind,
    private val property: JsonProperty,
    private val newVersion: String
) : BaseIntentionAction() {
    override fun getText(): String {
        return QuickFixesCommon.getPositionPrefix(
            kind,
            NUDSettingsState.instance.defaultUpdateType!!.ordinal
        ) + "Update to ${kind.toString().lowercase()} version ($newVersion)"
    }

    override fun getFamilyName() = "Update dependency"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        QuickFixesCommon.getAvailability(editor, file)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file == null) {
            printlnError("Trying to update dependency but file is null")
            return
        }
        val prefix = NUDHelper.Regex.semverPrefix.find(property.value?.stringValue() ?: "")?.value ?: ""
        val newElement = NUDHelper.createElement(project, "\"$prefix$newVersion\"", "JSON")
        NUDHelper.safeFileWrite(file, "Update \"${property.name}\" to $newVersion") {
            property.value?.replace(newElement)
        }
    }
}
