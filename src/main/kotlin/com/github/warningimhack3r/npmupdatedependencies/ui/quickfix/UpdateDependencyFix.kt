package com.github.warningimhack3r.npmupdatedependencies.ui.quickfix

import com.github.warningimhack3r.npmupdatedependencies.backend.Versions
import com.github.warningimhack3r.npmupdatedependencies.ui.helper.NUDHelper
import com.github.warningimhack3r.npmupdatedependencies.backend.stringValue
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class UpdateDependencyFix(private val kind: Versions.Kind, private val property: JsonProperty, private val newVersion: String, private val order: Int?): BaseIntentionAction() {
    override fun getText() = "${if (order != null) "$order. " else ""}Update to ${kind.text} version ($newVersion)"
    override fun getFamilyName() = "Update dependency"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return editor != null && file?.name == "package.json"
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val prefix = NUDHelper.Regex.semverPrefix.find(property.value?.stringValue() ?: "")?.value ?: ""
        val tempFile = "\"$prefix$newVersion\""
        val newElement = NUDHelper.createElement(project, tempFile, "JSON")
        NUDHelper.asyncWrite(project) {
            property.value?.replace(newElement)
        }
    }
}
