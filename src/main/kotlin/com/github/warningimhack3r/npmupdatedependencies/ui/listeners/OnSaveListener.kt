package com.github.warningimhack3r.npmupdatedependencies.ui.listeners

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Versions
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.ActionsCommon
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.NUDHelper
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Point
import javax.swing.JLabel

class OnSaveListener(val project: Project) : FileDocumentManagerListener {

    override fun beforeDocumentSaving(document: Document) {
        val state = NUDState.getInstance(project)
        // Initial checks
        val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
        val hasUpdates = state.availableUpdates.filter { it.value.data != null }.isNotEmpty()
        val hasDeprecations = state.deprecations.filter { it.value.data != null }.isNotEmpty()
        if (file.name != "package.json" || !NUDSettingsState.instance.autoFixOnSave
            || (!hasUpdates && !hasDeprecations)
        ) return

        // Create a set of actions to perform
        val actionsToPerform = mutableSetOf<() -> Unit>()

        // Fix updates if any
        if (hasUpdates) {
            actionsToPerform.add {
                ActionsCommon.updateAllDependencies(file, Versions.Kind.entries.first {
                    it == NUDSettingsState.instance.defaultUpdateType
                })
            }
        }

        // Fix deprecations if any
        if (hasDeprecations) {
            when (Deprecation.Action.entries.first {
                it == NUDSettingsState.instance.defaultDeprecationAction
            }) {
                Deprecation.Action.REPLACE -> actionsToPerform.add {
                    ActionsCommon.replaceAllDeprecations(file)
                }

                Deprecation.Action.REMOVE -> actionsToPerform.add {
                    ActionsCommon.deleteAllDeprecations(file)
                }

                Deprecation.Action.IGNORE -> {
                    // Do nothing, not selectable
                }
            }
        }

        // Perform the actions
        NUDHelper.safeFileWrite(file, "Applying fixes on save") {
            actionsToPerform.forEach { it() }
            showTooltip("Applied ${actionsToPerform.size} fixes on save")
        }
    }

    @RequiresEdt
    private fun showTooltip(text: String) {
        if (project.isDisposed) return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val cursorAbsoluteLocation = editor.visualPositionToXY(editor.caretModel.visualPosition)

        // Show the tooltip
        HintManager.getInstance().showHint(
            JLabel(text),
            RelativePoint.fromScreen(
                Point(
                    editor.contentComponent.locationOnScreen.x + cursorAbsoluteLocation.x,
                    editor.component.locationOnScreen.y + cursorAbsoluteLocation.y - editor.scrollingModel.verticalScrollOffset
                )
            ),
            HintManager.HIDE_BY_CARET_MOVE
                    or HintManager.HIDE_BY_ESCAPE
                    or HintManager.HIDE_BY_TEXT_CHANGE
                    or HintManager.HIDE_BY_OTHER_HINT
                    or HintManager.HIDE_BY_SCROLLING
                    or HintManager.HIDE_IF_OUT_OF_EDITOR,
            -1
        )
    }
}
