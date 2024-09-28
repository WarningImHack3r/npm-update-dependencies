package com.github.warningimhack3r.npmupdatedependencies.ui.quickfix

import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.stringValue
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Update
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.NUDHelper
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.QuickFixesCommon
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class UpdatePackageManagerFix(
    private val property: JsonProperty,
    update: Update
) : BaseIntentionAction() {
    companion object {
        private val log = logger<UpdatePackageManagerFix>()
    }

    private val packageManager = property.value?.stringValue()?.substringBefore("@")
    private val targetVersion = update.versions.latest

    override fun getText() = "1. Update $packageManager to $targetVersion"

    override fun getFamilyName() = "Update package manager"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
        QuickFixesCommon.getAvailability(editor, file)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file == null) {
            log.warn("Trying to update package manager but file is null")
            return
        }
        val newElement = NUDHelper.createElement(project, "\"$packageManager@${targetVersion}\"", "JSON")
        NUDHelper.safeFileWrite(file, "Update $packageManager to $targetVersion") {
            property.value?.replace(newElement)
        }
    }
}
