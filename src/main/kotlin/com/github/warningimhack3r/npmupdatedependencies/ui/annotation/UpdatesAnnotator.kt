package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers.PackageUpdateChecker
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.stringValue
import com.github.warningimhack3r.npmupdatedependencies.backend.models.DataState
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Property
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Update
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Versions.Kind
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.ActionsCommon
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.AnnotatorsCommon
import com.github.warningimhack3r.npmupdatedependencies.ui.quickfix.BlacklistVersionFix
import com.github.warningimhack3r.npmupdatedependencies.ui.quickfix.UpdateDependencyFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.json.psi.JsonProperty
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.applyIf
import org.semver4j.Semver
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class UpdatesAnnotator : DumbAware, ExternalAnnotator<
        Pair<Project, List<Property>>,
        Map<JsonProperty, Update>
        >() {
    companion object {
        private val log = logger<UpdatesAnnotator>()
    }

    override fun collectInformation(file: PsiFile): Pair<Project, List<Property>> =
        file.project to ActionsCommon.getAllDependencies(file).map { dependency ->
            Property(dependency, dependency.name, dependency.value?.stringValue())
        }

    @OptIn(ExperimentalTime::class)
    override fun doAnnotate(collectedInfo: Pair<Project, List<Property>>): Map<JsonProperty, Update> {
        val (project, info) = collectedInfo
        if (info.isEmpty()) return emptyMap()

        AnnotatorsCommon.beforeAnnotate(project)

        val state = NUDState.getInstance(project)
        val updateChecker = PackageUpdateChecker.getInstance(project)

        state.isScanningForUpdates = true
        log.debug("Scanning for updates...")
        // Remove from the cache all properties that are no longer in the file
        val fileDependenciesNames = info.map { property -> property.name }
        state.availableUpdates.keys.removeAll { key -> !fileDependenciesNames.contains(key) }
        // Update the status bar widget
        state.totalPackages = info.size
        state.scannedUpdates = 0
        log.debug("Starting batching ${info.size} dependencies for updates")
        return AnnotatorsCommon.runParallelTasks(
            info,
            NUDSettingsState.getInstance().maxParallelism.let { if (it == 100) null else it },
            { state.scannedUpdates++ }
        ) task@{ property ->
            val value = property.comparator ?: return@task null.also {
                log.debug("Empty comparator for ${property.name}, skipping")
            }

            val update = updateChecker.checkAvailableUpdates(property.name, value)
            state.availableUpdates[property.name] = state.availableUpdates[property.name].let { currentState ->
                if (currentState == null || currentState.data != update) DataState(
                    data = update,
                    addedAt = Clock.System.now(),
                    comparator = value
                ) else currentState
            }
            val coerced = Semver.coerce(value)
            val updateAvailable =
                update != null && ((coerced != null && !update.versions.isEqualToAny(coerced)) || coerced == null)

            log.debug("Update found: $updateAvailable")
            if (updateAvailable) {
                property.jsonProperty to update
            } else null
        }.filterNotNull().toMap().also {
            log.debug("Updates scanned, ${it.size} found out of ${info.size}")
            state.isScanningForUpdates = false
        }
    }

    override fun apply(file: PsiFile, annotationResult: Map<JsonProperty, Update>, holder: AnnotationHolder) {
        if (annotationResult.isNotEmpty()) log.debug("Creating annotations...")
        annotationResult.forEach { (property, scanResult) ->
            val versions = scanResult.versions
            val wasNonNumeric = property.value?.stringValue()?.none { it.isDigit() } == true
            val text = (if (wasNonNumeric) {
                "Avoid using a non-numeric version, replace it with its numeric equivalent."
            } else "An update is available!") + when (val channel = scanResult.channel) {
                is Update.Channel.Other -> " (channel: ${channel.name})"
                else -> ""
            } + if (scanResult.affectedByFilters.isNotEmpty()) {
                " (The following filters affected the result: ${scanResult.affectedByFilters.joinToString(", ")})"
            } else ""
            val currentVersion = property.value?.stringValue()?.let { Semver.coerce(it) }
            holder.newAnnotation(HighlightSeverity.WARNING, text)
                .range(property.value!!.textRange)
                .highlightType(ProblemHighlightType.WARNING)
                .withFix(UpdateDependencyFix(versions, Kind.LATEST, property))
                .applyIf(versions.satisfies != null) {
                    withFix(UpdateDependencyFix(versions, Kind.SATISFIES, property))
                }
                // Exclude next Major/Minor/Exact/all versions
                .applyIf(currentVersion != null && !wasNonNumeric) {
                    if (currentVersion == null) return@applyIf this

                    val baseIndex = if (versions.satisfies == null) -1 else 0
                    // Couldn't find a way to create them in a loop here
                    withFix(
                        BlacklistVersionFix(
                            baseIndex, property.name,
                            "${currentVersion.major + 1}.x.x"
                        )
                    )
                    withFix(
                        BlacklistVersionFix(
                            baseIndex + 1, property.name,
                            "${currentVersion.major}.${currentVersion.minor + 1}.x"
                        )
                    )
                    withFix(
                        BlacklistVersionFix(
                            baseIndex + 2, property.name,
                            versions.satisfies?.version
                                ?: "${currentVersion.major}.${currentVersion.minor}.${currentVersion.patch + 1}"
                        )
                    )
                    withFix(
                        BlacklistVersionFix(
                            baseIndex + 3, property.name,
                            "*", "ALL versions"
                        )
                    )
                }
                .create()
        }
        if (annotationResult.isNotEmpty()) log.debug("Annotations created")
    }
}
