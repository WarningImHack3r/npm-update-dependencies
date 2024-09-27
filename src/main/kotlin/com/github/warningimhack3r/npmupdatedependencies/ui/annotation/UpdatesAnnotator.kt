package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.RegistriesScanner
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers.PackageUpdateChecker
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.parallelMap
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.stringValue
import com.github.warningimhack3r.npmupdatedependencies.backend.models.DataState
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Property
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Update
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Versions.Kind
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
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
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import org.semver4j.Semver

class UpdatesAnnotator : DumbAware, ExternalAnnotator<
        Pair<Project, List<Property>>,
        Map<JsonProperty, Update>
        >() {
    companion object {
        private val log = logger<UpdatesAnnotator>()
    }

    override fun collectInformation(file: PsiFile): Pair<Project, List<Property>> =
        Pair(file.project, AnnotatorsCommon.getInfo(file))

    override fun doAnnotate(collectedInfo: Pair<Project, List<Property>>): Map<JsonProperty, Update> {
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

        val updateChecker = PackageUpdateChecker.getInstance(project)
        val maxParallelism = NUDSettingsState.instance.maxParallelism
        var activeTasks = 0

        log.debug("Scanning for updates...")
        return info
            .also { properties ->
                // Remove from the cache all properties that are no longer in the file
                val fileDependenciesNames = properties.map { property -> property.name }
                state.availableUpdates.keys.removeAll { key -> !fileDependenciesNames.contains(key) }
                // Update the status bar widget
                state.totalPackages = properties.size
                state.scannedUpdates = 0
                state.isScanningForUpdates = true
                log.debug("Starting batching ${info.size} dependencies for updates")
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
                    update != null && coerced != null && !update.versions.isEqualToAny(coerced)
                log.debug("Task finished for ${property.name}, update found: $updateAvailable")
                state.scannedUpdates++
                activeTasks--

                if (updateAvailable) {
                    Pair(property.jsonProperty, update)
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
            val text = "An update is available!" + if (scanResult.affectedByFilters.isNotEmpty()) {
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
                .applyIf(currentVersion != null) {
                    if (currentVersion == null) return@applyIf this

                    val baseIndex = if (versions.satisfies == null) -1 else 0
                    // Couldn't find a way to create them in a loop here
                    withFix(
                        BlacklistVersionFix(
                            baseIndex + 0, property.name,
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
                .needsUpdateOnTyping()
                .create()
        }
        if (annotationResult.isNotEmpty()) log.debug("Annotations created")
    }
}
