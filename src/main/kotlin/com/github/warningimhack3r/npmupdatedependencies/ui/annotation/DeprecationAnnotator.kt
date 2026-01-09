package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers.PackageDeprecationChecker
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.stringValue
import com.github.warningimhack3r.npmupdatedependencies.backend.models.DataState
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Property
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.ActionsCommon
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DeprecationAnnotator : DumbAware, ExternalAnnotator<
        Pair<Project, List<Property>>,
        Map<JsonProperty, Deprecation>
        >() {
    companion object {
        private val log = logger<DeprecationAnnotator>()
    }

    override fun collectInformation(file: PsiFile): Pair<Project, List<Property>> =
        file.project to ActionsCommon.getAllDependencies(file).map { dependency ->
            Property(dependency, dependency.name, dependency.value?.stringValue())
        }

    @OptIn(ExperimentalTime::class)
    override fun doAnnotate(collectedInfo: Pair<Project, List<Property>>): Map<JsonProperty, Deprecation> {
        val (project, info) = collectedInfo
        if (info.isEmpty()) return emptyMap()

        AnnotatorsCommon.beforeAnnotate(project)

        val state = NUDState.getInstance(project)
        val deprecationChecker = PackageDeprecationChecker.getInstance(project)

        state.isScanningForDeprecations = true
        log.debug("Scanning for deprecations...")
        // Remove from the cache all deprecations that are no longer in the file
        val fileDependenciesNames = info.map { property -> property.name }
        state.deprecations.keys.removeAll { key -> !fileDependenciesNames.contains(key) }
        // Update the status bar widget
        state.totalPackages = info.size
        state.scannedDeprecations = 0
        log.debug("Starting batching ${info.size} dependencies for deprecation")
        return AnnotatorsCommon.runParallelTasks(
            info,
            NUDSettingsState.getInstance().maxParallelism.let { if (it == 100) null else it },
            { state.scannedDeprecations++ }
        ) task@{ property ->
            val value = property.comparator ?: return@task null.also {
                log.debug("Empty comparator for ${property.name}, skipping")
            }

            val deprecation = deprecationChecker.getDeprecationStatus(property.name, value)
            state.deprecations[property.name] = state.deprecations[property.name].let { currentState ->
                if (currentState == null || currentState.data != deprecation) DataState(
                    data = deprecation,
                    addedAt = Clock.System.now(),
                    comparator = value
                ) else currentState
            }

            log.debug("Deprecation found: ${deprecation != null}")
            deprecation?.let {
                property.jsonProperty to it
            }
        }.filterNotNull().toMap().also {
            log.debug("Deprecations scanned, ${it.size} found out of ${info.size}")
            state.isScanningForDeprecations = false
        }
    }

    override fun apply(file: PsiFile, annotationResult: Map<JsonProperty, Deprecation>, holder: AnnotationHolder) {
        if (annotationResult.isNotEmpty()) log.debug("Creating annotations...")
        annotationResult.forEach { (property, deprecation) ->
            when (deprecation.kind) {
                Deprecation.Kind.DEPRECATED -> {
                    holder.newAnnotation(HighlightSeverity.ERROR, deprecation.reason)
                        .range(property.textRange)
                        .highlightType(ProblemHighlightType.LIKE_DEPRECATED)
                        .applyIf(deprecation.replacement != null) {
                            withFix(
                                DeprecatedDependencyFix(
                                    deprecation.kind,
                                    property,
                                    Deprecation.Action.REPLACE,
                                    deprecation.replacement
                                )
                            )
                        }
                        .withFix(
                            DeprecatedDependencyFix(
                                deprecation.kind,
                                property,
                                Deprecation.Action.REMOVE,
                                null,
                                deprecation.replacement?.let { emptyList() }
                            )
                        )
                        .create()
                }

                Deprecation.Kind.UNMAINTAINED -> {
                    holder.newAnnotation(HighlightSeverity.WEAK_WARNING, deprecation.reason)
                        .range(property.textRange)
                        .highlightType(ProblemHighlightType.WEAK_WARNING)
                        .withFix(
                            DeprecatedDependencyFix(
                                deprecation.kind,
                                property,
                                Deprecation.Action.REMOVE,
                                null,
                                listOf(Deprecation.Action.REPLACE)
                            )
                        )
                        .withFix(
                            DeprecatedDependencyFix(
                                deprecation.kind,
                                property,
                                Deprecation.Action.IGNORE,
                                null,
                                listOf(Deprecation.Action.REPLACE)
                            )
                        )
                        .create()
                }
            }
        }
        if (annotationResult.isNotEmpty()) {
            log.debug("Annotations created, updating banner")
            EditorNotifications.getInstance(file.project).updateAllNotifications()
        }
    }
}
