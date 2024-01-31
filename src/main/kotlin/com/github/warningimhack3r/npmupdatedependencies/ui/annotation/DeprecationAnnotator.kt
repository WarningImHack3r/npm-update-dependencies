package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Property
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NPMJSClient
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.RegistriesScanner
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.parallelMap
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.AnnotatorsCommon
import com.github.warningimhack3r.npmupdatedependencies.ui.quickfix.DeprecatedDependencyFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.json.psi.JsonProperty
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.applyIf

class DeprecationAnnotator : DumbAware, ExternalAnnotator<
        Pair<Project, List<Property>>,
        Map<JsonProperty, Deprecation>
>() {

    override fun collectInformation(file: PsiFile): Pair<Project, List<Property>> = Pair(file.project, AnnotatorsCommon.getInfo(file))

    override fun doAnnotate(collectedInfo: Pair<Project, List<Property>>): Map<JsonProperty, Deprecation> {
        val (project, info) = collectedInfo
        if (info.isEmpty()) return emptyMap()

        if (!project.service<NUDState>().isScanningForRegistries && project.service<NUDState>().packageRegistries.isEmpty()) {
            project.service<NUDState>().isScanningForRegistries = true
            RegistriesScanner.scan()
            project.service<NUDState>().isScanningForRegistries = false
        }

        while (project.service<NUDState>().isScanningForRegistries || project.service<NUDState>().isScanningForDeprecations) {
            // Wait for the registries to be scanned and avoid multiple scans at the same time
        }

        return info
            .also {
                // Remove from the cache all deprecations that are no longer in the file
                val fileDependenciesNames = it.map { property -> property.name }
                val state = project.service<NUDState>()
                state.deprecations.keys.removeAll { key -> !fileDependenciesNames.contains(key) }
                // Update the status bar widget
                state.totalPackages = it.size
                state.scannedDeprecations = 0
                state.isScanningForDeprecations = true
            }.parallelMap { property ->
                project.service<NUDState>().deprecations[property.name]?.let { deprecation ->
                    project.service<NUDState>().scannedDeprecations++
                    // If the deprecation is already in the cache, we don't need to check the NPM registry
                    Pair(property.jsonProperty, deprecation)
                } ?: project.service<NPMJSClient>().getPackageDeprecation(property.name)?.let { reason ->
                    // Get the deprecation reason and check if it contains a package name
                    reason.split(" ").map { word ->
                        // Remove punctuation at the end of the word
                        word.replace(Regex("[,;.]$"), "")
                    }.filter { word ->
                        // Try to find a word that looks like a package name
                        if (word.startsWith("@")) {
                            // Scoped package
                            return@filter word.split("/").size == 2
                        }
                        if (word.contains("/")) {
                            // If it contains a slash without being a scoped package, it's likely an URL
                            return@filter false
                        }
                        // Other potential matches
                        if (word.contains("-")) {
                            return@filter word.lowercase() == word
                        }
                        // Else if we're unsure, we don't consider it as a package name
                        false
                    }.parallelMap innerMap@ { potentialPackage ->
                        // Confirm that the word is a package name by trying to get its latest version
                        project.service<NPMJSClient>().getLatestVersion(potentialPackage)?.let {
                            Pair(potentialPackage, it)
                        }
                    }.filterNotNull().also {
                        project.service<NUDState>().scannedDeprecations++
                    }.firstOrNull()?.let { (name, version) ->
                        // We found a package name and its latest version, so we can create a replacement
                        Pair(property.jsonProperty, Deprecation(reason, Deprecation.Replacement(name, version)))
                    } ?: Pair(property.jsonProperty, Deprecation(reason, null)) // No replacement found in the deprecation reason
                }.also { pair ->
                    val state = project.service<NUDState>()
                    pair?.let {
                        // Add the deprecation to the cache if any
                        state.deprecations[property.name] = it.second
                    } ?: run {
                        // Remove the deprecation from the cache if no deprecation is found
                        if (state.deprecations.containsKey(property.name)) {
                            state.deprecations.remove(property.name)
                        }
                    }
                }
            }.filterNotNull().toMap().also {
                project.service<NUDState>().isScanningForDeprecations = false
            }
    }

    override fun apply(file: PsiFile, annotationResult: Map<JsonProperty, Deprecation>, holder: AnnotationHolder) {
        annotationResult.forEach { (property, deprecation) ->
            holder.newAnnotation(HighlightSeverity.ERROR, deprecation.reason)
                .range(property.textRange)
                .highlightType(ProblemHighlightType.LIKE_DEPRECATED)
                .applyIf(deprecation.replacement != null) {
                    withFix(DeprecatedDependencyFix(property, deprecation.replacement!!.name, deprecation.replacement.version, Deprecation.Action.REPLACE, true))
                }
                .withFix(DeprecatedDependencyFix(property, "", "", Deprecation.Action.REMOVE, Deprecation.Action.values().size.run {
                    this - (if (deprecation.replacement == null) 1 else 0)
                } > 1))
                .needsUpdateOnTyping()
                .create()
        }
        if (annotationResult.isNotEmpty()) {
            EditorNotifications.getInstance(file.project).updateAllNotifications()
        }
    }
}
