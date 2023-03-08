package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.NPMJSClient
import com.github.warningimhack3r.npmupdatedependencies.backend.PackageUpdateChecker
import com.github.warningimhack3r.npmupdatedependencies.backend.parallelMap
import com.github.warningimhack3r.npmupdatedependencies.ui.quickfix.ReplaceDependencyFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.json.psi.JsonProperty
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import com.intellij.util.applyIf

class DeprecationAnnotator: DumbAware, ExternalAnnotator<List<Property>, Map<JsonProperty, Deprecation>>() {

    override fun collectInformation(file: PsiFile): List<Property>? = AnnotatorsCommon.getInfo(file)

    override fun doAnnotate(collectedInfo: List<Property>?): Map<JsonProperty, Deprecation> {
        if (collectedInfo.isNullOrEmpty()) return emptyMap()

        return collectedInfo
            .parallelMap { property ->
                PackageUpdateChecker.deprecations[property.name]?.let { deprecation ->
                    Pair(property.jsonProperty, deprecation)
                } ?: NPMJSClient.getPackageDeprecation(property.name)?.let { reason ->
                    reason.split(" ").map { word ->
                        word.replace(Regex("[,;.]$"), "")
                    }.filter { word ->
                        word.startsWith("@") || word.contains("/") || word.contains("-")
                    }.parallelMap innerMap@ { potentialPackage ->
                        val version = NPMJSClient.getLatestVersion(potentialPackage) ?: return@innerMap null
                        Pair(potentialPackage, version)
                    }.filterNotNull().firstOrNull()?.let { (name, version) ->
                        Pair(property.jsonProperty, Deprecation(reason, Deprecation.Replacement(name, version)))
                    } ?: Pair(property.jsonProperty, Deprecation(reason, null))
                }.also { pair ->
                    pair?.let {
                        PackageUpdateChecker.deprecations[property.name] = it.second
                    } ?: run {
                        if (PackageUpdateChecker.deprecations.containsKey(property.name)) {
                            PackageUpdateChecker.deprecations.remove(property.name)
                        }
                    }
                }
            }.filterNotNull().toMap()
    }

    override fun apply(file: PsiFile, annotationResult: Map<JsonProperty, Deprecation>, holder: AnnotationHolder) {
        annotationResult.forEach { result ->
            holder.newAnnotation(HighlightSeverity.ERROR, result.value.reason)
                .range(result.key.textRange)
                .highlightType(ProblemHighlightType.LIKE_DEPRECATED)
                .applyIf(result.value.replacement != null) {
                    withFix(ReplaceDependencyFix(result.key, result.value.replacement!!.name, result.value.replacement!!.version))
                }
                .needsUpdateOnTyping()
                .create()
        }
    }
}
