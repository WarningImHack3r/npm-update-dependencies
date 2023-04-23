package com.github.warningimhack3r.npmupdatedependencies.ui.actions.scan

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.availableUpdates
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.deprecations
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class InvalidateCachesAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = availableUpdates.isNotEmpty() || deprecations.isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        availableUpdates.clear()
        deprecations.clear()
    }
}
