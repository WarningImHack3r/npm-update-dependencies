package com.github.warningimhack3r.npmupdatedependencies.ui.annotation

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.RegistriesScanner
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers.PackageUpdateChecker
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.stringValue
import com.github.warningimhack3r.npmupdatedependencies.backend.models.DataState
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Property
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Update
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.ActionsCommon
import com.github.warningimhack3r.npmupdatedependencies.ui.quickfix.BlacklistVersionFix
import com.github.warningimhack3r.npmupdatedependencies.ui.quickfix.UpdatePackageManagerFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.json.psi.JsonProperty
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.applyIf
import kotlinx.datetime.Clock
import org.semver4j.Semver

class PackageManagerAnnotator : DumbAware, ExternalAnnotator<
        Pair<Project, Property>,
        Pair<JsonProperty, Update>
        >() {
    companion object {
        private val log = logger<PackageManagerAnnotator>()
    }

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Pair<Project, Property>? {
        return ActionsCommon.getPackageManager(file)?.let { property ->
            file.project to Property(
                property,
                property.name,
                property.value?.stringValue()
            ).also {
                log.debug("Found package manager: ${it.comparator}")
            }
        }
    }

    override fun doAnnotate(collectedInfo: Pair<Project, Property>?): Pair<JsonProperty, Update>? {
        if (collectedInfo == null) return null
        val (project, property) = collectedInfo

        val state = NUDState.getInstance(project)
        val registriesScanner = RegistriesScanner.getInstance(project)
        if (!registriesScanner.scanned && !state.isScanningForRegistries) {
            log.debug("Registries not scanned yet, scanning now")
            state.isScanningForRegistries = true
            registriesScanner.scan()
            state.isScanningForRegistries = false
            log.debug("Registries scanned")
        }

        log.info("Starting checking for package manager update")
        state.foundPackageManager = null
        state.isScanningForPackageManager = true

        if (property.comparator == null) return null
        if (!property.comparator.contains("@") // not a valid package manager format
            || property.comparator.split("@").size != 2 // more than one @
            || property.comparator.startsWith("@") // no package manager name
            || property.comparator.endsWith("@") // no version
        ) {
            log.warn("Invalid package manager: ${property.comparator}")
            return null
        }

        val (managerName, managerVersion) = property.comparator.substringBefore("+").split("@")
        val update = PackageUpdateChecker.getInstance(project)
            .checkAvailableUpdates(managerName, "^${managerVersion}")
        state.availableUpdates[managerName] = state.availableUpdates[managerName].let { currentState ->
            if (currentState == null || currentState.data != update) DataState(
                data = update,
                addedAt = Clock.System.now(),
                comparator = managerVersion
            ) else currentState
        }
        state.isScanningForPackageManager = false
        if (update == null) {
            log.debug("No update found for $managerName")
            return null
        }
        log.info("Found update for $managerName: $update")
        state.foundPackageManager = managerName
        return property.jsonProperty to update
    }

    override fun apply(file: PsiFile, annotationResult: Pair<JsonProperty, Update>?, holder: AnnotationHolder) {
        if (annotationResult == null) return
        val (property, update) = annotationResult
        log.debug("Applying annotation for ${property.name}")
        val message = "An update is available!" + if (update.affectedByFilters.isNotEmpty()) {
            " (The following filters affected the result: ${update.affectedByFilters.joinToString(", ")})"
        } else ""
        val (managerName, managerVersion) = property.value?.stringValue()?.split("@") ?: return
        val currentVersion = Semver.coerce(managerVersion)
        holder.newAnnotation(HighlightSeverity.WARNING, message)
            .range(property.value!!.textRange)
            .highlightType(ProblemHighlightType.WARNING)
            .withFix(UpdatePackageManagerFix(property, update))
            .applyIf(currentVersion != null) {
                if (currentVersion == null) return@applyIf this

                withFix(
                    BlacklistVersionFix(
                        -1, managerName,
                        "${currentVersion.major + 1}.x.x"
                    )
                )
                withFix(
                    BlacklistVersionFix(
                        0, managerName,
                        "${currentVersion.major}.${currentVersion.minor + 1}.x"
                    )
                )
                withFix(
                    BlacklistVersionFix(
                        1, managerName,
                        "${currentVersion.major}.${currentVersion.minor}.${currentVersion.patch + 1}"
                    )
                )
                withFix(
                    BlacklistVersionFix(
                        2, managerName,
                        "*", "ALL versions"
                    )
                )
            }
            .needsUpdateOnTyping()
            .create()

        log.debug("Annotation applied for ${property.name}")
    }
}
