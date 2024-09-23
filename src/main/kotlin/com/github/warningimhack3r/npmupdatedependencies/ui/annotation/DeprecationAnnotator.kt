package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.data.DataState
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Property
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.RegistriesScanner
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers.PackageDeprecationChecker
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
import java.util.Date

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

        val deprecationChecker = PackageDeprecationChecker.getInstance(project)
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
                val value = property.comparator ?: return@parallelMap null.also {
                    log.debug("Empty comparator for ${property.name}, skipping")
                    state.scannedUpdates++
                    activeTasks--
                }

                val deprecation = deprecationChecker.getDeprecationStatus(property.name, value)
                state.deprecations[property.name] = state.deprecations.getOrPut(property.name) {
                    DataState(
                        data = deprecation,
                        scannedAt = Date(),
                        comparator = value
                    )
                }
                log.debug("Task finished for ${property.name}, deprecation found: ${deprecation != null}")
                state.scannedDeprecations++
                activeTasks--

                deprecation?.let {
                    Pair(property.jsonProperty, it)
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
