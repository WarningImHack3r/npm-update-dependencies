package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.stringValue
import com.intellij.json.psi.JsonProperty
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

data class Property(
    val jsonProperty: JsonProperty,
    val name: String,
    val comparator: String?
)

object AnnotatorsCommon {
    fun getInfo(file: PsiFile): List<Property>? {
        if (file.name != "package.json") return null
        return PsiTreeUtil.findChildrenOfType(file, JsonProperty::class.java)
            .filter { child ->
                (child.parent.parent as? JsonProperty)?.name in listOf("dependencies", "devDependencies")
            }.map {
                Property(it, it.name, it.value?.stringValue())
            }
    }
}
