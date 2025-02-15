package com.github.warningimhack3r.npmupdatedependencies.ui.listeners

import com.github.warningimhack3r.npmupdatedependencies.NUDConstants.PACKAGE_JSON
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
        val hasDeprecations = state.deprecations.filter {
            it.value.data?.kind == Deprecation.Kind.DEPRECATED
        }.isNotEmpty()
        val hasUnmaintained = state.deprecations.filter {
            it.value.data?.kind == Deprecation.Kind.UNMAINTAINED
        }.isNotEmpty()
        if (file.name != PACKAGE_JSON || !NUDSettingsState.instance.autoFixOnSave
            || (!hasUpdates && !hasDeprecations)
        ) return

        // Create a set of actions to perform
        val actionsToPerform = mutableListOf<() -> Unit>()

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
            when (NUDSettingsState.instance.defaultDeprecationAction) {
                Deprecation.Action.REPLACE -> actionsToPerform.add {
                    ActionsCommon.replaceAllDeprecations(file)
                }

                Deprecation.Action.REMOVE -> actionsToPerform.add {
                    ActionsCommon.deleteAllDeprecations(file) {
                        it.data?.kind == Deprecation.Kind.DEPRECATED
                    }
                }

                else -> {
                    // Do nothing, IGNORE or null
                }
            }
        }

        // Fix unmaintained if any
        if (hasUnmaintained) {
            when (NUDSettingsState.instance.defaultUnmaintainedAction) {
                Deprecation.Action.REMOVE -> actionsToPerform.add {
                    ActionsCommon.deleteAllDeprecations(file) {
                        it.data?.kind == Deprecation.Kind.UNMAINTAINED
                    }
                }

                Deprecation.Action.IGNORE -> actionsToPerform.add {
                    ActionsCommon.ignoreAllDeprecations(file) {
                        it.data?.kind == Deprecation.Kind.UNMAINTAINED
                    }
                }

                else -> {
                    // Do nothing, REPLACE or null
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
