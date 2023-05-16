package com.github.warningimhack3r.npmupdatedependencies.ui.actions.deprecation

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.deprecations
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.ActionsCommon
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class ReplaceAllDeprecationsAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = deprecations.isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        ActionsCommon.replaceAllDeprecations(file)
    }
}