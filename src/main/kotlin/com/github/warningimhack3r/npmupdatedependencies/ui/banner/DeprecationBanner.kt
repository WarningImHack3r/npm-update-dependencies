package com.github.warningimhack3r.npmupdatedependencies.ui.banner

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.deprecations
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.ActionsCommon
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBColor
import com.intellij.util.applyIf

class DeprecationBanner : EditorNotifications.Provider<EditorNotificationPanel>() {
    companion object {
        val KEY = Key.create<EditorNotificationPanel>("NpmUpdateDependenciesDeprecationBanner")
    }

    override fun getKey(): Key<EditorNotificationPanel> = KEY

    override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
        val psiFile = PsiManager.getInstance(project).findFile(file)
        if (psiFile != null && file.name == "package.json"
            && deprecations.isNotEmpty() && NUDSettingsState.instance.showDeprecationBanner) {
            val deprecationsCount = deprecations.size
            return EditorNotificationPanel(JBColor.YELLOW.darker()).apply {
                // Description text & icon
                val actionsTitles = Deprecation.Action.values().mapIndexed { index, action ->
                    action.text.applyIf(index > 0) {
                        lowercase()
                    }
                }
                val actionsString = actionsTitles.dropLast(1).joinToString(", ") + " or " + actionsTitles.last()
                text(if (deprecationsCount > 1) {
                    "You have $deprecationsCount deprecated packages. $actionsString them"
                } else {
                    "$deprecationsCount package is deprecated. $actionsString it"
                } + " to avoid issues.")
                icon(AllIcons.General.Warning)

                // Actions
                listOf(Deprecation.Action.values().first {
                    it.ordinal == NUDSettingsState.instance.defaultDeprecationAction
                }).plus(Deprecation.Action.values().filter { action ->
                    action.ordinal != NUDSettingsState.instance.defaultDeprecationAction
                }).forEach { action ->
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
        return null
    }
}
