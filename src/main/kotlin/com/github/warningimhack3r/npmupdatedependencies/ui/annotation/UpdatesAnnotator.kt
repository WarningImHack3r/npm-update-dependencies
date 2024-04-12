package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Property
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions.Kind
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.PackageUpdateChecker
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.RegistriesScanner
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.parallelMap
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.AnnotatorsCommon
import com.github.warningimhack3r.npmupdatedependencies.ui.quickfix.UpdateDependencyFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.json.psi.JsonProperty
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.applyIf
import org.semver4j.Semver

class UpdatesAnnotator : DumbAware, ExternalAnnotator<
        Pair<Project, List<Property>>,
        Map<JsonProperty, Versions>
        >() {

    override fun collectInformation(file: PsiFile): Pair<Project, List<Property>> =
        Pair(file.project, AnnotatorsCommon.getInfo(file))

    override fun doAnnotate(collectedInfo: Pair<Project, List<Property>>): Map<JsonProperty, Versions> {
        val (project, info) = collectedInfo
        if (info.isEmpty()) return emptyMap()

        var state = project.service<NUDState>()
        if (!state.isScanningForRegistries && state.packageRegistries.isEmpty()) {
            state.isScanningForRegistries = true
            project.service<RegistriesScanner>().scan()
            state.isScanningForRegistries = false
        }

        while (state.isScanningForRegistries || state.isScanningForUpdates) {
            // Wait for the registries to be scanned and avoid multiple scans at the same time
        }

        state = project.service<NUDState>()
        val updateChecker = project.service<PackageUpdateChecker>()
        return info
            .also {
                // Remove from the cache all properties that are no longer in the file
                val fileDependenciesNames = it.map { property -> property.name }
                state.availableUpdates.keys.removeAll { key -> !fileDependenciesNames.contains(key) }
                // Update the status bar widget
                state.totalPackages = it.size
                state.scannedUpdates = 0
                state.isScanningForUpdates = true
            }.parallelMap { property ->
                val value = property.comparator ?: return@parallelMap null
                val newVersion = updateChecker.areUpdatesAvailable(property.name, value)
                state.scannedUpdates++
                if (newVersion != null && !newVersion.isEqualToAny(Semver(value))) Pair(
                    property.jsonProperty,
                    newVersion
                ) else null
            }.filterNotNull().toMap().also {
                state.isScanningForUpdates = false
            }
    }

    override fun apply(file: PsiFile, annotationResult: Map<JsonProperty, Versions>, holder: AnnotationHolder) {
        annotationResult.forEach { (property, versions) ->
            holder.newAnnotation(
                HighlightSeverity.WARNING, "${
                    if (versions.orderedAvailableKinds().size > 1) "${versions.orderedAvailableKinds().size} u" else "U"
                }pdate${
                    if (versions.orderedAvailableKinds().size > 1) "s" else ""
                } available"
            )
                .range(property.value!!.textRange)
                .highlightType(ProblemHighlightType.WARNING)
                .applyIf(versions.satisfies != null) {
                    withFix(UpdateDependencyFix(Kind.SATISFIES, property, versions.satisfies!!.version, true))
                }
                .withFix(
                    UpdateDependencyFix(
                        Kind.LATEST,
                        property,
                        versions.latest.version,
                        versions.orderedAvailableKinds().size > 1
                    )
                )
                // TODO: Add Exclude Major/Minor/Exact/All versions
                // TODO: Mention excluded versions in the tooltip if affected
                .needsUpdateOnTyping()
                .create()
        }
    }
}
