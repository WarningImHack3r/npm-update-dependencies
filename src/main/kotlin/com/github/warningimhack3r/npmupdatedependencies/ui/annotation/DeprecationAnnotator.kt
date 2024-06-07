package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Property
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NPMJSClient
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.RegistriesScanner
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.parallelMap
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
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
import kotlinx.coroutines.delay

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

        val state = NUDState.getInstance(project)
        val registriesScanner = RegistriesScanner.getInstance(project)
        if (!registriesScanner.scanned && !state.isScanningForRegistries) {
            log.debug("Registries not scanned yet, scanning now")
            state.isScanningForRegistries = true
            registriesScanner.scan()
            state.isScanningForRegistries = false
            log.debug("Registries scanned")
        }

        val npmjsClient = NPMJSClient.getInstance(project)
        val maxParallelism = NUDSettingsState.instance.maxParallelism
        var activeTasks = 0

        log.debug("Scanning for deprecations...")
        return info
            .also { properties ->
                // Remove from the cache all deprecations that are no longer in the file
                val fileDependenciesNames = properties.map { property -> property.name }
                state.deprecations.keys.removeAll { key -> !fileDependenciesNames.contains(key) }
                // Update the status bar widget
                state.totalPackages = properties.size
                state.scannedDeprecations = 0
                state.isScanningForDeprecations = true
                log.debug("Starting batching ${info.size} dependencies for deprecation")
            }.parallelMap { property ->
                if (maxParallelism < 100) {
                    while (activeTasks >= maxParallelism) {
                        // Wait for the active tasks count to decrease
                        delay(50)
                    }
                    activeTasks++
                    log.debug("Task $activeTasks/$maxParallelism started: ${property.name}")
                }
                state.deprecations[property.name]?.let { deprecation ->
                    log.debug("Deprecation found in cache: ${property.name}")
                    state.scannedDeprecations++
                    activeTasks--
                    // If the deprecation is already in the cache, we don't need to check the NPM registry
                    Pair(property.jsonProperty, deprecation)
                } ?: npmjsClient.getPackageDeprecation(property.name)?.let { reason ->
                    // Get the deprecation reason and check if it contains a package name
                    reason.split(" ").map { word ->
                        // Remove punctuation at the end of the word
                        word.replace(Regex("[,;.]$"), "")
                    }.filter { word ->
                        with(word) {
                            // Try to find a word that looks like a package name
                            when {
                                // Scoped package
                                startsWith("@") -> split("/").size == 2
                                // If it contains a slash without being a scoped package, it's likely an URL
                                contains("/") -> false
                                // Other potential matches
                                contains("-") -> lowercase() == this
                                // Else if we're unsure, we don't consider it as a package name
                                else -> false
                            }
                        }
                    }.parallelMap innerMap@{ potentialPackage ->
                        // Confirm that the word is a package name by trying to get its latest version
                        npmjsClient.getLatestVersion(potentialPackage)?.let {
                            Pair(potentialPackage, it)
                        }
                    }.filterNotNull().firstOrNull()?.let { (name, version) ->
                        // We found a package name and its latest version, so we can create a replacement
                        Pair(property.jsonProperty, Deprecation(reason, Deprecation.Replacement(name, version)))
                    } ?: Pair(
                        property.jsonProperty,
                        Deprecation(reason, null)
                    ) // No replacement found in the deprecation reason
                }.also { result ->
                    result?.let { (_, deprecation) ->
                        // Add the deprecation to the cache if any
                        state.deprecations[property.name] = deprecation
                    } ?: state.deprecations[property.name]?.let { _ ->
                        // Remove the deprecation from the cache if no deprecation is found
                        state.deprecations.remove(property.name)
                    }

                    log.debug("Finished task for ${property.name}, deprecation found: ${result != null}")
                    // Manage counters
                    state.scannedDeprecations++
                    activeTasks--
                }
            }.filterNotNull().toMap().also {
                log.debug("Deprecations scanned, ${it.size} found out of ${info.size}")
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
