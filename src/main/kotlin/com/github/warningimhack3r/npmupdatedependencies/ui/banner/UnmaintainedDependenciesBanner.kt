package com.github.warningimhack3r.npmupdatedependencies.ui.banner

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.ActionsCommon
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent

class UnmaintainedDependenciesBanner : EditorNotificationProvider {
    companion object {
        private val log = logger<DeprecationBanner>()
    }

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        val psiFile = PsiManager.getInstance(project).findFile(file)
        val state = NUDState.getInstance(project)
        val unmaintainedDependencies = state.deprecations.filter { deprecation ->
            deprecation.value.data?.kind == Deprecation.Kind.UNMAINTAINED
        }
        if (psiFile == null || file.name != "package.json" || unmaintainedDependencies.isEmpty() || !NUDSettingsState.instance.showUnmaintainedBanner) {
            when {
                psiFile == null -> log.warn("Leaving: cannot find PSI file for ${file.name} @ ${file.path}")
                unmaintainedDependencies.isEmpty() -> {
                    if (state.scannedDeprecations > 0) log.debug("Leaving: no deprecations found")
                    else log.warn("Leaving: deprecations not scanned yet")
                }

                !NUDSettingsState.instance.showUnmaintainedBanner -> log.debug("Leaving: unmaintained banner is disabled")
            }
            return null
        }
        return Function { _ ->
            EditorNotificationPanel().apply {
                // Description text & icon
                text(
                    if (unmaintainedDependencies.size > 1) {
                        "${unmaintainedDependencies.size} unmaintained dependencies found in this project. Consider looking for alternatives."
                    } else {
                        "${unmaintainedDependencies.size} unmaintained dependency found in this project. Consider looking for an alternative."
                    }
                )
                icon(AllIcons.General.Information)

                // Actions
                Deprecation.Action.orderedActions(
                    NUDSettingsState.instance.defaultUnmaintainedAction
                ).filter { it != Deprecation.Action.REPLACE }.forEach { action ->
                    createActionLabel(
                        action.toString() + if (unmaintainedDependencies.size > 1) {
                            " them"
                        } else " \"${unmaintainedDependencies.keys.firstOrNull()}\""
                    ) {
                        when (action) {
                            Deprecation.Action.REPLACE -> {
                                // Replace unmaintained dependencies, cannot happen
                            }

                            Deprecation.Action.REMOVE -> ActionsCommon.deleteAllDeprecations(psiFile) {
                                it.data?.kind == Deprecation.Kind.UNMAINTAINED
                            }

                            Deprecation.Action.IGNORE -> ActionsCommon.ignoreAllDeprecations(psiFile) {
                                it.data?.kind == Deprecation.Kind.UNMAINTAINED
                            }
                        }
                    }
                }

                // Don't show again
                createActionLabel("Don't show again") {
                    NUDSettingsState.instance.showUnmaintainedBanner = false
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }
            }
        }
    }
}
