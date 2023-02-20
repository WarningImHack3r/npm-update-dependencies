package com.github.warningimhack3r.npmupdatedependencies.ui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class NoUpdateAction: AnAction(
    "No Updates Available",
    "No updates available for this file",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        // Do nothing, this is a placeholder action
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = false
    }
}
