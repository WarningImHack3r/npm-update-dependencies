package com.github.warningimhack3r.npmupdatedependencies.ui.actions.scan

import com.github.warningimhack3r.npmupdatedependencies.backend.PackageUpdateChecker
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.CollaborationToolsIcons

class InvalidateCachesAction: AnAction(
    "Invalidate Scans Caches",
    "Clear temporary caches used by the plugin to store scan results",
    CollaborationToolsIcons.DeleteHovered
) {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = PackageUpdateChecker.availableUpdates.isNotEmpty()
                || PackageUpdateChecker.deprecations.isNotEmpty()
    }
    override fun actionPerformed(e: AnActionEvent) {
        PackageUpdateChecker.availableUpdates.clear()
        PackageUpdateChecker.deprecations.clear()
    }
}
