package com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NPMJSClient
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.parallelMap
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

@Service(Service.Level.PROJECT)
class PackageDeprecationChecker(private val project: Project) : PackageChecker() {
    companion object {
        private val log = logger<PackageDeprecationChecker>()

        @JvmStatic
        fun getInstance(project: Project): PackageDeprecationChecker = project.service()
    }

    fun getDeprecationStatus(packageName: String, comparator: String): Deprecation? {
        log.info("Checking for deprecations for $packageName with comparator $comparator")
        val state = NUDState.getInstance(project)
        if (!isComparatorUpgradable(comparator)) {
            log.warn("Comparator $comparator is not upgradable")
            if (state.deprecations.containsKey(packageName)) {
                log.debug("Removing cached deprecation for $packageName")
                state.deprecations.remove(packageName)
            }
            return null
        }

        // Check if a deprecation has already been found
        state.deprecations[packageName]?.let { deprecationState ->
            log.debug("Deprecation found in cache for $packageName: $deprecationState")
            if (deprecationState.comparator != comparator) {
                log.debug("Comparator for $packageName has changed, removing cached deprecation")
                state.deprecations.remove(packageName)
                return@let
            }
            if (deprecationState.scannedAt.plus(
                    NUDSettingsState.instance.cacheDurationMinutes.minutes
                ) < Clock.System.now()
            ) {
                log.debug("Cached deprecation for $packageName has expired, removing it")
                state.deprecations.remove(packageName)
                return@let
            }
            return deprecationState.data
        } ?: log.debug("No cached deprecation found in cache for $packageName")

        // Check if the package is deprecated
        val npmjsClient = NPMJSClient.getInstance(project)
        val reason = npmjsClient.getPackageDeprecation(packageName) ?: return null

        // Get the deprecation reason and check if it contains a package name
        val replacementPackage = reason.split(" ").map { word ->
            // Remove punctuation at the end of the word
            word.replace(Regex("[,;.]$"), "")
        }.filter { word ->
            with(word) {
                // Try to find a word that looks like a package name
                when {
                    // Scoped package
                    startsWith("@") -> split("/").size == 2
                    // If it contains a slash without being a scoped package, it's likely a URL
                    contains("/") -> false
                    // Other potential matches, if they're lowercase and contain a dash
                    contains("-") -> lowercase() == this
                    // Else if we're unsure, we don't consider it as a package name
                    else -> false
                }
            }
        }.parallelMap { potentialPackage ->
            // Confirm that the word is a package name by trying to get its latest version
            npmjsClient.getLatestVersion(potentialPackage)?.let {
                Pair(potentialPackage, it)
            }
        }.filterNotNull().firstOrNull()

        return replacementPackage?.let { (name, version) ->
            // We found a package name and its latest version, so we can create a replacement
            Deprecation(reason, Deprecation.Replacement(name, version))
        } ?: Deprecation(reason, null)
    }
}
