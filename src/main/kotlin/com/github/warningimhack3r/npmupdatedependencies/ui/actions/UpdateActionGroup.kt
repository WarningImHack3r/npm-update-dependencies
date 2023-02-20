package com.github.warningimhack3r.npmupdatedependencies.ui.actions

import com.github.warningimhack3r.npmupdatedependencies.backend.PackageUpdateChecker
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class UpdateActionGroup: ActionGroup() {
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = editor != null && file?.name == "package.json"
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return if (PackageUpdateChecker.availableUpdates.isNotEmpty())
            arrayOf(UpdateAllLatestAction(), UpdateAllSatisfiesAction())
            else arrayOf(NoUpdateAction())
    }
}
