package com.github.warningimhack3r.npmupdatedependencies.ui.actions

import com.github.warningimhack3r.npmupdatedependencies.backend.Versions.Kind
import com.github.warningimhack3r.npmupdatedependencies.ui.icons.NUDIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class UpdateAllLatestAction: AnAction(
    "Update All (Latest)",
    "Bump all dependencies to their latest version, ignoring their satisfied range",
    NUDIcons.NPM
) {
    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        ActionsCommon.updateAll(file, Kind.LATEST)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.icon = NUDIcons.NPM
    }
}
