package com.github.warningimhack3r.npmupdatedependencies.ui.actions.scan

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger

class InvalidateCachesAction : AnAction() {
    companion object {
        private val log = logger<InvalidateCachesAction>()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project?.let { project ->
            val state = NUDState.getInstance(project)
            state.availableUpdates.isNotEmpty() || state.deprecations.isNotEmpty()
        } == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        log.debug("Cache invalidation requested")
        val state = e.project?.let { NUDState.getInstance(it) } ?: return.also {
            log.warn("No project found")
        }
        state.invalidateCaches()
        log.debug("Cache invalidated")
    }
}
