package com.github.warningimhack3r.npmupdatedependencies.ui.actions.update

import com.github.warningimhack3r.npmupdatedependencies.backend.PackageUpdateChecker
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.util.applyIf

class UpdateActionGroup: ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return if (PackageUpdateChecker.availableUpdates.isNotEmpty())
            arrayOf<AnAction>(UpdateAllLatestAction())
                .applyIf(PackageUpdateChecker.availableUpdates.values.map { it.satisfies }.any()) {
                    this@applyIf + UpdateAllSatisfiesAction()
                }
        else arrayOf(NoUpdateAction())
    }
}
