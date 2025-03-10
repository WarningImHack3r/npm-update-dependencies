package com.github.warningimhack3r.npmupdatedependencies.ui.banner

import com.github.warningimhack3r.npmupdatedependencies.NUDConstants.PACKAGE_JSON
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
import com.intellij.ui.JBColor
import com.intellij.util.applyIf
import java.util.function.Function
import javax.swing.JComponent

class DeprecationBanner : EditorNotificationProvider {
    companion object {
        private val log = logger<DeprecationBanner>()
    }

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        val psiFile = PsiManager.getInstance(project).findFile(file)
        val state = NUDState.getInstance(project)
        val foundDeprecations = state.deprecations.filter { deprecation ->
            deprecation.value.data?.kind == Deprecation.Kind.DEPRECATED
        }
        if (psiFile == null || file.name != PACKAGE_JSON || foundDeprecations.isEmpty() || !NUDSettingsState.instance.showDeprecationBanner) {
            when {
                psiFile == null -> log.warn("Leaving: cannot find PSI file for ${file.name} @ ${file.path}")
                foundDeprecations.isEmpty() -> {
                    if (state.scannedDeprecations > 0) log.debug("Leaving: no deprecations found")
                    else log.warn("Leaving: deprecations not scanned yet")
                }

                !NUDSettingsState.instance.showDeprecationBanner -> log.debug("Leaving: deprecation banner is disabled")
            }
            return null
        }
        return Function { _ ->
            EditorNotificationPanel(JBColor.YELLOW.darker()).apply {
                val availableActions = Deprecation.Action.entries.filter { action ->
                    // Exclude REPLACE if there are no replaceable deprecations
                    val isActionReplace = action == Deprecation.Action.REPLACE
                    val hasReplaceableDeprecations = foundDeprecations.values.any { state ->
                        state.data?.replacement != null
                    }
                    (isActionReplace && hasReplaceableDeprecations) || (!isActionReplace && action != Deprecation.Action.IGNORE)
                }
                // Description text & icon
                val actionsTitles = availableActions.mapIndexed { index, action ->
                    action.toString().applyIf(index > 0) {
                        lowercase()
                    }
                }
                val actionsString = if (actionsTitles.size > 1) {
                    actionsTitles.dropLast(1).joinToString(", ") + " or " + actionsTitles.last()
                } else {
                    actionsTitles.first()
                }
                val deprecationsCount = foundDeprecations.size
                text(
                    if (deprecationsCount > 1) {
                        "$deprecationsCount deprecated packages found in this project. $actionsString them"
                    } else {
                        "$deprecationsCount package is deprecated in this project. $actionsString it"
                    } + " to avoid issues."
                )
                icon(AllIcons.General.Warning)

                // Actions
                Deprecation.Action.orderedActions(
                    NUDSettingsState.instance.defaultDeprecationAction
                ).filter { availableActions.contains(it) }.forEach { action ->
                    createActionLabel(
                        action.toString() + if (deprecationsCount > 1) {
                            " deprecations"
                        } else " \"${foundDeprecations.keys.firstOrNull()}\""
                    ) {
                        when (action) {
                            Deprecation.Action.REPLACE -> ActionsCommon.replaceAllDeprecations(psiFile)
                            Deprecation.Action.REMOVE -> ActionsCommon.deleteAllDeprecations(psiFile) {
                                it.data?.kind == Deprecation.Kind.DEPRECATED
                            }

                            Deprecation.Action.IGNORE -> {
                                // Ignore all deprecations, won't happen as they can all be removed
                            }
                        }
                    }
                }

                // Don't show again
                createActionLabel("Don't show again") {
                    NUDSettingsState.instance.showDeprecationBanner = false
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }
            }
        }
    }
}
