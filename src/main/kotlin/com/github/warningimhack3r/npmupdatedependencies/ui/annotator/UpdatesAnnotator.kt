package com.github.warningimhack3r.npmupdatedependencies.ui.annotator

import com.github.warningimhack3r.npmupdatedependencies.backend.PackageUpdateChecker
import com.github.warningimhack3r.npmupdatedependencies.backend.Versions
import com.github.warningimhack3r.npmupdatedependencies.backend.Versions.Kind
import com.github.warningimhack3r.npmupdatedependencies.backend.stringValue
import com.github.warningimhack3r.npmupdatedependencies.ui.quickfix.UpdateDependencyFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.json.psi.JsonProperty
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.applyIf

class UpdatesAnnotator: ExternalAnnotator<Pair<Project, PsiFile>, Map<JsonProperty, Versions>>() {
    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Pair<Project, PsiFile>? = if (file.name == "package.json") Pair(editor.project!!, file) else null

    override fun doAnnotate(collectedInfo: Pair<Project, PsiFile>?): Map<JsonProperty, Versions> {
        if (collectedInfo == null || collectedInfo.second.name != "package.json") return emptyMap()

        val map = mutableMapOf<JsonProperty, Versions>()
        ReadAction.nonBlocking {
            PsiTreeUtil.findChildrenOfType(collectedInfo.second, JsonProperty::class.java)
                .filter { child ->
                    (child.parent.parent as? JsonProperty)?.name in listOf("dependencies", "devDependencies")
                }.mapNotNull { property ->
                    val value = property.value?.stringValue() ?: return@mapNotNull null
                    val (isUpdateAvailable, newVersion) = PackageUpdateChecker.hasUpdateAvailable(property.name, value)
                    if (isUpdateAvailable && !newVersion!!.isEqualToAny(value)) Pair(property, newVersion) else null
                }.forEach { (property, newVersion) ->
                    map[property] = newVersion
                }
        }.executeSynchronously()
        return map
    }

    override fun apply(file: PsiFile, annotationResult: Map<JsonProperty, Versions>, holder: AnnotationHolder) {
        annotationResult.forEach { result ->
            holder.newAnnotation(HighlightSeverity.WARNING, "${
                if (result.value.orderedAvailableKinds().size > 1) "${result.value.orderedAvailableKinds().size} u" else "U"
                }pdate${
                    if (result.value.orderedAvailableKinds().size > 1) "s" else ""
                } available")
                .range(result.key.value!!.textRange)
                .highlightType(ProblemHighlightType.WARNING)
                .applyIf(result.value.satisfies != null) {
                    withFix(UpdateDependencyFix(Kind.SATISFIES, result.key, result.value.satisfies!!, 1))
                }
                .withFix(UpdateDependencyFix(Kind.LATEST, result.key, result.value.latest, if (result.value.orderedAvailableKinds().size == 1) null else 2))
                .create()
        }
    }
}
