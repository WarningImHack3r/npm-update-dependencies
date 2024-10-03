package com.github.warningimhack3r.npmupdatedependencies.ui.quickfix

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.stringValue
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.ActionsCommon
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.NUDHelper
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.QuickFixesCommon
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement

class DeprecatedDependencyFix(
    private val property: JsonProperty,
    private val actionType: Deprecation.Action,
    replacement: Deprecation.Replacement?,
    private val showOrder: Boolean
) : BaseIntentionAction() {
    private val log = logger<DeprecatedDependencyFix>()
    private val replacementName = replacement?.name ?: ""
    private val replacementVersion = replacement?.version ?: ""

    override fun getText(): String {
        val baseText = when (actionType) {
            Deprecation.Action.REPLACE -> "Replace by \"${replacementName}\" (${replacementVersion})"
            Deprecation.Action.REMOVE -> "Remove dependency"
            Deprecation.Action.IGNORE -> "Ignore deprecation"
        }
        return (if (showOrder) QuickFixesCommon.getPositionPrefix(
            actionType,
            Deprecation.Action.orderedActions(NUDSettingsState.instance.defaultDeprecationAction!!)
        ) else "") + baseText
    }

    override fun getFamilyName(): String = "Replace or remove deprecated dependency"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        QuickFixesCommon.getAvailability(editor, file)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file == null) {
            log.warn("Trying to ${actionType.toString().lowercase()} dependency but the file is null")
            return
        }
        when (actionType) {
            Deprecation.Action.REPLACE -> {
                val prefix = NUDHelper.Regex.semverPrefix.find(property.value?.stringValue() ?: "")?.value ?: ""
                val newName = NUDHelper.createElement(project, "\"$replacementName\"", "JSON")
                val newVersion = NUDHelper.createElement(project, "\"$prefix$replacementVersion\"", "JSON")
                NUDHelper.safeFileWrite(file, "Replace \"${property.name}\" by \"$replacementName\"", false) {
                    property.nameElement.replace(newName)
                    property.value?.replace(newVersion)
                }
                if (NUDSettingsState.instance.autoReorderDependencies) ActionsCommon.reorderAllDependencies(file)
            }

            Deprecation.Action.REMOVE -> NUDHelper.safeFileWrite(file, "Delete \"${property.name}\"") {
                // Delete the comma before or after the property
                NUDHelper.getClosestElementMatching(
                    { it.text == "," },
                    property,
                    LeafPsiElement::class.java
                ).also {
                    if (it == null) {
                        log.warn("No comma found before or after the dependency (${property.name}) to delete")
                    }
                }?.delete()
                // Delete the property
                property.delete()
            }

            Deprecation.Action.IGNORE -> {
                NUDSettingsState.instance.excludedUnmaintainedPackages += ",${property.name}"
                DaemonCodeAnalyzer.getInstance(project).restart(file)
            }
        }
        NUDState.getInstance(project).deprecations.remove(property.name)
        ActionsCommon.deprecationsCompletion(project)
    }
}
