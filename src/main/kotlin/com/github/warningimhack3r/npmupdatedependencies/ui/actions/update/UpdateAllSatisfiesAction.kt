package com.github.warningimhack3r.npmupdatedependencies.ui.actions.update

import com.github.warningimhack3r.npmupdatedependencies.backend.Versions.Kind
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class UpdateAllSatisfiesAction: AnAction(
    "Update All (Satisfying)",
    "Bump all dependencies, matching their satisfying range",
    AllIcons.Actions.Download
) {
    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        UpdateActionsCommon.updateAll(file, Kind.SATISFIES)
    }
}
