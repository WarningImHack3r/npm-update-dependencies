package com.github.warningimhack3r.npmupdatedependencies.ui.helpers

import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.stringValue
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Property
import com.intellij.json.psi.JsonProperty
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

object AnnotatorsCommon {
    fun getInfo(file: PsiFile): List<Property> {
        if (file.name != "package.json") return emptyList()
        return PsiTreeUtil.findChildrenOfType(file, JsonProperty::class.java)
            .filter { child ->
                (child.parent.parent as? JsonProperty)?.name in listOf("dependencies", "devDependencies")
            }.map {
                Property(it, it.name, it.value?.stringValue())
            }
    }
}
