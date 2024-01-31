package com.github.warningimhack3r.npmupdatedependencies.ui.actions.scan

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class InvalidateCachesAction : AnAction() {
    override fun update(e: AnActionEvent) {
        val state = e.project?.service<NUDState>()
        e.presentation.isEnabled = if (state != null) {
            state.availableUpdates.isNotEmpty() || state.deprecations.isNotEmpty()
        } else false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val state = e.project?.service<NUDState>() ?: return
        state.availableUpdates.clear()
        state.deprecations.clear()
    }
}
