package com.github.warningimhack3r.npmupdatedependencies.ui.helpers

import com.github.warningimhack3r.npmupdatedependencies.NUDConstants.PACKAGE_JSON
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

object QuickFixesCommon {
    fun <T : Enum<*>> getPositionPrefix(enumValue: T, orderedValues: List<T>): String {
        return "${orderedValues.indexOf(enumValue) + 1}. "
    }

    fun getAvailability(editor: Editor?, file: PsiFile?): Boolean {
        return editor != null && file?.name == PACKAGE_JSON
    }
}
