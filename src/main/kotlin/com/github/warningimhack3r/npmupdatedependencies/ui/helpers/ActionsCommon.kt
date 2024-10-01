package com.github.warningimhack3r.npmupdatedependencies.ui.helpers

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.stringValue
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Versions
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.EditorNotifications

object ActionsCommon {
    private val log = logger<ActionsCommon>()

    private fun getAllDependencies(file: PsiFile): List<JsonProperty> {
        return PsiTreeUtil.findChildrenOfType(file, JsonProperty::class.java)
            .filter {
                (it.parent.parent as? JsonProperty)?.name in listOf("dependencies", "devDependencies")
            }
    }

    fun updateAll(file: PsiFile, kind: Versions.Kind) {
        val availableUpdates = NUDState.getInstance(file.project).availableUpdates
        getAllDependencies(file)
            .mapNotNull { property ->
                availableUpdates[property.name]?.data?.let { update ->
                    val newVersion = update.versions.from(kind)
                        ?: update.versions.orderedAvailableKinds(kind).firstOrNull { it != kind }?.let {
                            update.versions.from(it)
                        } ?: return@let null
                    val prefix =
                        NUDHelper.Regex.semverPrefix.find(property.value?.stringValue() ?: "")?.value ?: ""
                    val newElement = NUDHelper.createElement(property.project, "\"$prefix$newVersion\"", "JSON")
                    Pair(property, newElement)
                }
            }.run {
                if (isNotEmpty()) {
                    NUDHelper.safeFileWrite(file, "Update all dependencies", false) {
                        forEach { (property, newElement) ->
                            property.value?.replace(newElement)
                        }
                    }
                }
            }
    }

    fun replaceAllDeprecations(file: PsiFile) {
        val deprecations = NUDState.getInstance(file.project).deprecations
        getAllDependencies(file)
            .mapNotNull { property ->
                deprecations[property.name]?.data?.let { deprecation ->
                    val replacement = deprecation.replacement ?: return@let null
                    val prefix =
                        NUDHelper.Regex.semverPrefix.find(property.value?.stringValue() ?: "")?.value ?: ""
                    val newNameElement =
                        NUDHelper.createElement(property.project, "\"${replacement.name}\"", "JSON")
                    val newVersionElement =
                        NUDHelper.createElement(property.project, "\"$prefix${replacement.version}\"", "JSON")
                    Triple(property, newNameElement, newVersionElement)
                }
            }.run {
                if (isNotEmpty()) {
                    NUDHelper.safeFileWrite(file, "Replace all deprecations", false) {
                        forEach { (property, newNameElement, newVersionElement) ->
                            property.nameElement.replace(newNameElement)
                            property.value?.replace(newVersionElement)
                        }
                    }
                    if (NUDSettingsState.instance.autoReorderDependencies) reorderAllDependencies(file)
                }
                deprecationsCompletion(file.project)
            }
    }

    fun reorderAllDependencies(file: PsiFile) {
        // Get all dependencies, split them into two lists (dev and normal) and sort each list
        val dependencies = getAllDependencies(file)
        val devDependencies = dependencies.filter {
            (it.parent.parent as? JsonProperty)?.name == "devDependencies"
        }
        val normalDependencies = dependencies.filter {
            (it.parent.parent as? JsonProperty)?.name == "dependencies"
        }
        val sortedDevDependencies = devDependencies.sortedBy { it.name }
        val sortedNormalDependencies = normalDependencies.sortedBy { it.name }

        // Loop through all devDependencies and store them in a map with the old dependency and a copy of the new one
        val sortedDependenciesMap = mutableMapOf<JsonProperty, JsonProperty>()
        if (devDependencies != sortedDevDependencies) {
            devDependencies.forEachIndexed { index, jsonProperty ->
                if (jsonProperty != sortedDevDependencies[index]) {
                    sortedDependenciesMap[jsonProperty] = sortedDevDependencies[index].copy() as JsonProperty
                }
            }
        }

        // Loop through all normalDependencies and store them in the same map the same way as above
        if (normalDependencies != sortedNormalDependencies) {
            normalDependencies.forEachIndexed { index, jsonProperty ->
                if (jsonProperty != sortedNormalDependencies[index]) {
                    sortedDependenciesMap[jsonProperty] = sortedNormalDependencies[index].copy() as JsonProperty
                }
            }
        }

        // Finally, loop through the map and replace the old dependency with the new one with safeFileWrite
        NUDHelper.safeFileWrite(file, "Reordering dependencies", false) {
            sortedDependenciesMap.forEach { (oldDependency, newDependency) ->
                oldDependency.nameElement.replace(newDependency.nameElement.copy())
                if (newDependency.value != null) {
                    oldDependency.value?.replace(newDependency.value!!.copy())
                }
            }
        }
    }

    fun deleteAllDeprecations(file: PsiFile, predicate: (JsonProperty) -> Boolean = { true }) {
        val deprecations = NUDState.getInstance(file.project).deprecations
        val deprecationsToRemove = deprecations.filter { it.value.data != null }
        getAllDependencies(file)
            .filter { property ->
                deprecationsToRemove.containsKey(property.name) && predicate(property)
            }.run {
                if (isNotEmpty()) {
                    NUDHelper.safeFileWrite(file, "Delete all deprecations", false) {
                        forEach { property ->
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
                    }
                }
                deprecationsToRemove.forEach { (key, _) ->
                    deprecations.remove(key)
                }
                deprecationsCompletion(file.project)
            }
    }

    fun deprecationsCompletion(project: Project) {
        EditorNotifications.getInstance(project).updateAllNotifications()
    }
}
