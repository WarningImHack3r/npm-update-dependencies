package com.github.warningimhack3r.npmupdatedependencies.ui.actions.deprecation

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class DeprecationActionGroup: ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(ReplaceAllDeprecationsAction())
    }
}
