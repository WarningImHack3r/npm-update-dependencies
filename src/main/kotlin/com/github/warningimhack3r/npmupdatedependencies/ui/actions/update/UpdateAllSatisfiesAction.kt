package com.github.warningimhack3r.npmupdatedependencies.ui.actions.update

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions.Kind
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.ActionsCommon
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service

class UpdateAllSatisfiesAction : AnAction() {
    override fun update(e: AnActionEvent) {
        val availableUpdates = e.project?.service<NUDState>()?.availableUpdates
        e.presentation.isEnabled = if (availableUpdates != null) {
            availableUpdates.isNotEmpty()
                    && availableUpdates.values.mapNotNull { it.satisfies }.any()
        } else false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        ActionsCommon.updateAll(file, Kind.SATISFIES)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
