package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Property
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NPMJSClient
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState.deprecations
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState.isScanningForDeprecations
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState.isScanningForRegistries
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState.packageRegistries
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.RegistriesScanner
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.parallelMap
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.AnnotatorsCommon
import com.github.warningimhack3r.npmupdatedependencies.ui.quickfix.DeprecatedDependencyFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.json.psi.JsonProperty
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.applyIf

class DeprecationAnnotator : DumbAware, ExternalAnnotator<List<Property>, Map<JsonProperty, Deprecation>>() {

    override fun collectInformation(file: PsiFile): List<Property>? = AnnotatorsCommon.getInfo(file)

    override fun doAnnotate(collectedInfo: List<Property>?): Map<JsonProperty, Deprecation> {
        if (collectedInfo.isNullOrEmpty()) return emptyMap()

        if (!isScanningForRegistries && packageRegistries.isEmpty()) {
            isScanningForRegistries = true
            RegistriesScanner.scan()
            isScanningForRegistries = false
        }

        while (isScanningForRegistries) {
            // Wait for the registries to be scanned
        }

        return collectedInfo
            .also {
                // Remove from the cache all deprecations that are no longer in the file
                val fileDependenciesNames = it.map { property -> property.name }
                deprecations.keys.removeAll { key -> !fileDependenciesNames.contains(key) }
                // Update the status bar widget
                isScanningForDeprecations = true
            }.parallelMap { property ->
                deprecations[property.name]?.let { deprecation ->
                    // If the deprecation is already in the cache, we don't need to check the NPM registry
                    Pair(property.jsonProperty, deprecation)
                } ?: NPMJSClient.getPackageDeprecation(property.name)?.let { reason ->
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
                        val version = NPMJSClient.getLatestVersion(potentialPackage) ?: return@innerMap null
                        Pair(potentialPackage, version)
                    }.filterNotNull().firstOrNull()?.let { (name, version) ->
                        // We found a package name and its latest version, so we can create a replacement
                        Pair(property.jsonProperty, Deprecation(reason, Deprecation.Replacement(name, version)))
                    } ?: Pair(property.jsonProperty, Deprecation(reason, null)) // No replacement found in the deprecation reason
                }.also { pair ->
                    pair?.let {
                        // Add the deprecation to the cache if any
                        deprecations[property.name] = it.second
                    } ?: run {
                        // Remove the deprecation from the cache if no deprecation is found
                        if (deprecations.containsKey(property.name)) {
                            deprecations.remove(property.name)
                        }
                    }
                }
            }.filterNotNull().toMap().also {
                isScanningForDeprecations = false
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
