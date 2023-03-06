package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.intellij.lang.ExternalAnnotatorsFilter
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.psi.PsiFile

class UpdatesAnnotatorFilter: ExternalAnnotatorsFilter {
    override fun isProhibited(annotator: ExternalAnnotator<*, *>?, file: PsiFile?): Boolean {
        return !(annotator is UpdatesAnnotator && file?.name == "package.json")
    }
}
