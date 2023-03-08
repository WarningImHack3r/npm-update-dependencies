package com.github.warningimhack3r.npmupdatedependencies.ui.actions.update

import com.github.warningimhack3r.npmupdatedependencies.backend.PackageUpdateChecker
import com.github.warningimhack3r.npmupdatedependencies.backend.Versions
import com.github.warningimhack3r.npmupdatedependencies.ui.helper.NUDHelper
import com.github.warningimhack3r.npmupdatedependencies.backend.stringValue
import com.intellij.json.psi.JsonProperty
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

object UpdateActionsCommon {
    fun updateAll(file: PsiFile, kind: Versions.Kind) {
        PsiTreeUtil.findChildrenOfType(file, JsonProperty::class.java)
            .filter {
                (it.parent.parent as? JsonProperty)?.name in listOf("dependencies", "devDependencies")
            }
            .mapNotNull { property ->
                if (PackageUpdateChecker.availableUpdates.containsKey(property.name)) {
                    val newVersion = PackageUpdateChecker.availableUpdates[property.name]?.from(kind) ?: return@mapNotNull null
                    val prefix = NUDHelper.Regex.semverPrefix.find(property.value?.stringValue() ?: "")?.value ?: ""
                    val newElement = NUDHelper.createElement(property.project, "\"$prefix$newVersion\"", "JSON")
                    return@mapNotNull Pair(property, newElement)
                }
                return@mapNotNull null
            }.run {
                if (isNotEmpty()) {
                    NUDHelper.asyncWrite(file.project) {
                        forEach { (property, newElement) ->
                            property.value?.replace(newElement)
                        }
                    }
                }
            }
    }
}
