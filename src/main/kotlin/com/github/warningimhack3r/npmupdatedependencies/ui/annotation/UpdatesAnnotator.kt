package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.availableUpdates
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.isScanningForUpdates
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.PackageUpdateChecker
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Property
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions.Kind
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.parallelMap
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.AnnotatorsCommon
import com.github.warningimhack3r.npmupdatedependencies.ui.quickfix.UpdateDependencyFix
import com.github.warningimhack3r.npmupdatedependencies.ui.statusbar.StatusBarHelper
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

        return collectedInfo
            .also {
                // Remove from the cache all properties that are no longer in the file
                val fileDependenciesNames = it.map { property -> property.name }
                availableUpdates.keys.removeAll { key -> !fileDependenciesNames.contains(key) }
                // Update the status bar widget
                isScanningForUpdates = true
                StatusBarHelper.updateWidget()
            }.parallelMap { property ->
                val value = property.comparator ?: return@parallelMap null
                val (isUpdateAvailable, newVersion) = PackageUpdateChecker.hasUpdateAvailable(property.name, value)
                if (isUpdateAvailable && !newVersion!!.isEqualToAny(value)) Pair(
                    property.jsonProperty,
                    newVersion
                ) else null
            }.filterNotNull().toMap().also {
                isScanningForUpdates = false
                StatusBarHelper.updateWidget()
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
