package com.github.warningimhack3r.npmupdatedependencies.ui.actions

import com.github.warningimhack3r.npmupdatedependencies.ui.actions.deprecation.DeprecationActionGroup
import com.github.warningimhack3r.npmupdatedependencies.ui.actions.scan.ScanActionGroup
import com.github.warningimhack3r.npmupdatedependencies.ui.actions.update.UpdateActionGroup
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Separator

class MainActionGroup: ActionGroup() {
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = editor != null && file?.name == "package.json"
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            UpdateActionGroup(), Separator(), DeprecationActionGroup(), Separator(), ScanActionGroup()
        )
    }
}
