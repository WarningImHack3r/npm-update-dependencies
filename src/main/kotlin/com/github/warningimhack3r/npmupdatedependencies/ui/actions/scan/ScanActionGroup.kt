package com.github.warningimhack3r.npmupdatedependencies.ui.actions.scan

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ScanActionGroup: ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(InvalidateCachesAction())
    }
}
