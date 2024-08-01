package com.github.warningimhack3r.npmupdatedependencies.ui.actions.scan

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class InvalidateCachesAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val state = e.project?.let { NUDState.getInstance(it) }
        e.presentation.isEnabled = if (state != null) {
            state.availableUpdates.isNotEmpty() || state.deprecations.isNotEmpty()
        } else false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val state = e.project?.let { NUDState.getInstance(it) } ?: return
        state.availableUpdates.clear()
        state.deprecations.clear()
    }
}
