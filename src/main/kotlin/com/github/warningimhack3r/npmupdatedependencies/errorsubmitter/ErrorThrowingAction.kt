package com.github.warningimhack3r.npmupdatedependencies.errorsubmitter

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction

/**
 * Force an Exception from within this plugin. This action is only used for testing the plugin
 * [ErrorReportSubmitter][com.github.warningimhack3r.npmupdatedependencies.errorsubmitter.GitHubErrorReportSubmitter] extension point.
 *
 * This can be enabled by setting the below in `Help -> Diagnostic Tools -> Debug Log Settings...`:
 * ```
 * #com.github.warningimhack3r.npmupdatedependencies.errorreport.ErrorThrowingAction
 * ```
 */
class ErrorThrowingAction : DumbAwareAction() {
    companion object {
        private val LOG = logger<ErrorThrowingAction>()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = LOG.isDebugEnabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (Math.random() < 0.5) {
            throw StringIndexOutOfBoundsException("String Index Out Of Bounds! Yikes!")
        } else {
            throw ClassCastException("Class Cast Exception! Oh Boy!")
        }
    }
}
