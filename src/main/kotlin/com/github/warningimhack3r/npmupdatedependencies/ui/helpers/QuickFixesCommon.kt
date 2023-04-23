package com.github.warningimhack3r.npmupdatedependencies.ui.helpers

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

object QuickFixesCommon {
    fun getPositionPrefix(enumValue: Enum<*>, setting: Int): String {
        val position = if (setting == enumValue.ordinal) 1 else {
            enumValue.javaClass.enumConstants.filter {
                it.ordinal != setting
            }.indexOf(enumValue) + 2
        }
        return "$position. "
    }

    fun getAvailability(editor: Editor?, file: PsiFile?): Boolean {
        return editor != null && file?.name == "package.json"
    }
}
