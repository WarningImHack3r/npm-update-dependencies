package com.github.warningimhack3r.npmupdatedependencies.ui.actions.update

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Versions.Kind
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.ActionsCommon
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class UpdateAllSatisfiesAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val availableUpdates = e.project?.let { NUDState.getInstance(it) }?.availableUpdates
        e.presentation.isEnabled = availableUpdates?.let {
            availableUpdates.isNotEmpty()
                    && availableUpdates.values.any { it.data?.versions?.satisfies != null }
        } == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        ActionsCommon.updateAllDependencies(file, Kind.SATISFIES)
        ActionsCommon.updatePackageManager(file)
    }
}
