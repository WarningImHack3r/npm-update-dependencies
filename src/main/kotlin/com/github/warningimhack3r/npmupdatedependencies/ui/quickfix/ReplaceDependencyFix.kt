package com.github.warningimhack3r.npmupdatedependencies.ui.quickfix

import com.github.warningimhack3r.npmupdatedependencies.backend.stringValue
import com.github.warningimhack3r.npmupdatedependencies.ui.helper.NUDHelper
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class ReplaceDependencyFix(
    private val property: JsonProperty,
    private val replacementName: String,
    private val replacementVersion: String
): BaseIntentionAction() {

    override fun getText(): String = "Replace by \"${replacementName}\" (${replacementVersion})"
    override fun getFamilyName(): String = "Replace deprecated dependency"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return editor != null && file?.name == "package.json"
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val prefix = NUDHelper.Regex.semverPrefix.find(property.value?.stringValue() ?: "")?.value ?: ""
        val nameFileContent = "\"$replacementName\""
        val versionFileContent = "\"$prefix$replacementVersion\""
        val newName = NUDHelper.createElement(project, nameFileContent, "JSON")
        val newVersion = NUDHelper.createElement(project, versionFileContent, "JSON")
        NUDHelper.asyncWrite(project) {
            property.nameElement.replace(newName)
            property.value?.replace(newVersion)
        }
    }
}
