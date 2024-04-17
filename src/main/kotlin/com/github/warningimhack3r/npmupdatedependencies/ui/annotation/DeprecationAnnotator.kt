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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.applyIf

class DeprecationAnnotator : DumbAware, ExternalAnnotator<
        Pair<Project, List<Property>>,
        Map<JsonProperty, Deprecation>
        >() {
    companion object {
        private val log = logger<DeprecationAnnotator>()
    }

    override fun collectInformation(file: PsiFile): Pair<Project, List<Property>> =
        Pair(file.project, AnnotatorsCommon.getInfo(file))

    override fun doAnnotate(collectedInfo: Pair<Project, List<Property>>): Map<JsonProperty, Deprecation> {
        val (project, info) = collectedInfo
        if (info.isEmpty()) return emptyMap()

        var state = NUDState.getInstance(project)
        if (!state.isScanningForRegistries && state.packageRegistries.isEmpty()) {
            state.isScanningForRegistries = true
            log.debug("No registries found, scanning for registries...")
            RegistriesScanner.getInstance(project).scan()
            log.debug("Registries scanned")
            state.isScanningForRegistries = false
        }

        while (state.isScanningForRegistries || state.isScanningForDeprecations) {
            // Wait for the registries to be scanned and avoid multiple scans at the same time
            log.debug("Waiting for registries to be scanned...")
        }

        log.debug("Scanning for deprecations...")
        state = NUDState.getInstance(project)
        val npmjsClient = NPMJSClient.getInstance(project)
        return info
            .also {
                // Remove from the cache all deprecations that are no longer in the file
                val fileDependenciesNames = it.map { property -> property.name }
                state.deprecations.keys.removeAll { key -> !fileDependenciesNames.contains(key) }
                // Update the status bar widget
                state.totalPackages = it.size
                state.scannedDeprecations = 0
                state.isScanningForDeprecations = true
            }.parallelMap { property ->
                state.deprecations[property.name]?.let { deprecation ->
                    state.scannedDeprecations++
                    // If the deprecation is already in the cache, we don't need to check the NPM registry
                    Pair(property.jsonProperty, deprecation)
                } ?: npmjsClient.getPackageDeprecation(property.name)?.let { reason ->
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
                    }.parallelMap innerMap@{ potentialPackage ->
                        // Confirm that the word is a package name by trying to get its latest version
                        npmjsClient.getLatestVersion(potentialPackage)?.let {
                            Pair(potentialPackage, it)
                        }
                    }.filterNotNull().also {
                        state.scannedDeprecations++
                    }.firstOrNull()?.let { (name, version) ->
                        // We found a package name and its latest version, so we can create a replacement
                        Pair(property.jsonProperty, Deprecation(reason, Deprecation.Replacement(name, version)))
                    } ?: Pair(
                        property.jsonProperty,
                        Deprecation(reason, null)
                    ) // No replacement found in the deprecation reason
                }.also { pair ->
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
                log.debug("Deprecations scanned, ${it.size} found")
                state.isScanningForDeprecations = false
            }
    }

    override fun apply(file: PsiFile, annotationResult: Map<JsonProperty, Deprecation>, holder: AnnotationHolder) {
        if (annotationResult.isNotEmpty()) log.debug("Creating annotations...")
        annotationResult.forEach { (property, deprecation) ->
            holder.newAnnotation(HighlightSeverity.ERROR, deprecation.reason)
                .range(property.textRange)
                .highlightType(ProblemHighlightType.LIKE_DEPRECATED)
                .applyIf(deprecation.replacement != null) {
                    withFix(
                        DeprecatedDependencyFix(
                            property,
                            Deprecation.Action.REPLACE,
                            deprecation.replacement,
                            true
                        )
                    )
                }
                .withFix(
                    DeprecatedDependencyFix(
                        property,
                        Deprecation.Action.REMOVE,
                        null,
                        deprecation.replacement != null
                    )
                )
                .needsUpdateOnTyping()
                .create()
        }
        if (annotationResult.isNotEmpty()) {
            log.debug("Annotations created, updating banner")
            EditorNotifications.getInstance(file.project).updateAllNotifications()
        }
    }
}
