package com.github.warningimhack3r.npmupdatedependencies.ui.actions.deprecation

import com.github.warningimhack3r.npmupdatedependencies.backend.PackageUpdateChecker
import com.github.warningimhack3r.npmupdatedependencies.backend.stringValue
import com.github.warningimhack3r.npmupdatedependencies.ui.helper.NUDHelper
import com.intellij.icons.AllIcons
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.util.PsiTreeUtil

class ReplaceAllDeprecationsAction: AnAction(
    "Replace All Deprecations",
    "Replace all deprecated dependencies with their recommended replacements",
    AllIcons.Actions.SwapPanels
) {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = PackageUpdateChecker.deprecations.isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        PsiTreeUtil.findChildrenOfType(file, JsonProperty::class.java)
            .filter {
                (it.parent.parent as? JsonProperty)?.name in listOf("dependencies", "devDependencies")
            }.mapNotNull { property ->
                if (PackageUpdateChecker.deprecations.containsKey(property.name)) {
                    val newName = PackageUpdateChecker.deprecations[property.name]?.replacement?.name ?: return@mapNotNull null
                    val newVersion = PackageUpdateChecker.deprecations[property.name]?.replacement?.version ?: return@mapNotNull null
                    val prefix = NUDHelper.Regex.semverPrefix.find(property.value?.stringValue() ?: "")?.value ?: ""
                    val newElementName = NUDHelper.createElement(property.project, "\"$newName\"", "JSON")
                    val newElementVersion = NUDHelper.createElement(property.project, "\"$prefix$newVersion\"", "JSON")
                    return@mapNotNull Pair(property, Pair(newElementName, newElementVersion))
                }
                return@mapNotNull null
            }.run {
                if (isNotEmpty()) {
                    NUDHelper.asyncWrite(file.project) {
                        forEach { (property, newElements) ->
                            property.nameElement.replace(newElements.first)
                            property.value?.replace(newElements.second)
                        }
                    }
                }
            }
    }
}
