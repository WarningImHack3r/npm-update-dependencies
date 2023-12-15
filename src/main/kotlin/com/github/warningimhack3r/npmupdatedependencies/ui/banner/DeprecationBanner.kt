package com.github.warningimhack3r.npmupdatedependencies.ui.banner

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState.deprecations
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.ActionsCommon
import com.intellij.icons.AllIcons
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

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?> = Function { _ ->
        val psiFile = PsiManager.getInstance(project).findFile(file)
        if (psiFile == null || file.name != "package.json" || deprecations.isEmpty() || !NUDSettingsState.instance.showDeprecationBanner) {
            return@Function null
        }
        val deprecationsCount = deprecations.size
        return@Function EditorNotificationPanel(JBColor.YELLOW.darker()).apply {
            val availableActions = Deprecation.Action.values().filter { action ->
                (action == Deprecation.Action.REPLACE && deprecations.any { (_, deprecation) ->
                    deprecation.replacement != null
                }) || action != Deprecation.Action.REPLACE
            }
            // Description text & icon
            val actionsTitles = availableActions.mapIndexed { index, action ->
                action.text.applyIf(index > 0) {
                    lowercase()
                }
            }
            val actionsString = if (actionsTitles.size > 1) {
                actionsTitles.dropLast(1).joinToString(", ") + " or " + actionsTitles.last()
            } else {
                actionsTitles.first()
            }
            text(if (deprecationsCount > 1) {
                "You have $deprecationsCount deprecated packages. $actionsString them"
            } else {
                "$deprecationsCount package is deprecated. $actionsString it"
            } + " to avoid issues.")
            icon(AllIcons.General.Warning)

            // Actions
            listOf(availableActions.firstOrNull { action ->
                action.ordinal == NUDSettingsState.instance.defaultDeprecationAction
            }).plus(availableActions.filter { action ->
                action.ordinal != NUDSettingsState.instance.defaultDeprecationAction
            }).filterNotNull().forEach { action ->
                createActionLabel(action.text + if (deprecationsCount > 1) " them" else " it") {
                    when (action) {
                        Deprecation.Action.REPLACE -> ActionsCommon.replaceAllDeprecations(psiFile)
                        Deprecation.Action.REMOVE -> ActionsCommon.deleteAllDeprecations(psiFile)
                    }
                }
            }

            // Don't show again
            createActionLabel("Don't show again") {
                NUDSettingsState.instance.showDeprecationBanner = false
                EditorNotifications.getInstance(project).updateAllNotifications()
            }
        }
    }
}
