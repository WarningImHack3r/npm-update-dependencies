package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Property
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions.Kind
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState.availableUpdates
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState.isScanningForRegistries
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState.isScanningForUpdates
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState.packageRegistries
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState.scannedUpdates
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState.totalPackages
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
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import com.intellij.util.applyIf

class UpdatesAnnotator : DumbAware, ExternalAnnotator<List<Property>, Map<JsonProperty, Versions>>() {

    override fun collectInformation(file: PsiFile): List<Property>? = AnnotatorsCommon.getInfo(file)

    override fun doAnnotate(collectedInfo: List<Property>?): Map<JsonProperty, Versions> {
        if (collectedInfo.isNullOrEmpty()) return emptyMap()

        if (!isScanningForRegistries && packageRegistries.isEmpty()) {
            isScanningForRegistries = true
            RegistriesScanner.scan()
            isScanningForRegistries = false
        }

        while (isScanningForRegistries || isScanningForUpdates) {
            // Wait for the registries to be scanned and avoid multiple scans at the same time
        }

        return collectedInfo
            .also {
                // Remove from the cache all properties that are no longer in the file
                val fileDependenciesNames = it.map { property -> property.name }
                availableUpdates.keys.removeAll { key -> !fileDependenciesNames.contains(key) }
                // Update the status bar widget
                totalPackages = it.size
                scannedUpdates = 0
                isScanningForUpdates = true
            }.parallelMap { property ->
                val value = property.comparator ?: return@parallelMap null
                val (isUpdateAvailable, newVersion) = PackageUpdateChecker.hasUpdateAvailable(property.name, value)
                scannedUpdates++
                if (isUpdateAvailable && !newVersion!!.isEqualToAny(value)) Pair(
                    property.jsonProperty,
                    newVersion
                ) else null
            }.filterNotNull().toMap().also {
                isScanningForUpdates = false
            }
    }

    override fun apply(file: PsiFile, annotationResult: Map<JsonProperty, Versions>, holder: AnnotationHolder) {
        annotationResult.forEach { (property, versions) ->
            holder.newAnnotation(HighlightSeverity.WARNING, "${
                if (versions.orderedAvailableKinds().size > 1) "${versions.orderedAvailableKinds().size} u" else "U"
                }pdate${
                    if (versions.orderedAvailableKinds().size > 1) "s" else ""
                } available")
                .range(property.value!!.textRange)
                .highlightType(ProblemHighlightType.WARNING)
                .applyIf(versions.satisfies != null) {
                    withFix(UpdateDependencyFix(Kind.SATISFIES, property, versions.satisfies!!, true))
                }
                .withFix(UpdateDependencyFix(Kind.LATEST, property, versions.latest, versions.orderedAvailableKinds().size > 1))
                .needsUpdateOnTyping()
                .create()
        }
    }
}
