package com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NPMJSClient
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.parallelMap
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.toReadableString
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.periodUntil
import org.semver4j.Semver
import kotlin.time.Duration.Companion.days
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
        val now = Clock.System.now()
        state.deprecations[packageName]?.let { deprecationState ->
            log.debug("Deprecation found in cache for $packageName: $deprecationState")
            if (deprecationState.comparator != comparator) {
                log.debug("Comparator for $packageName has changed, removing cached deprecation")
                state.deprecations.remove(packageName)
                return@let
            }
            if (now > deprecationState.addedAt + NUDSettingsState.instance.cacheDurationMinutes.minutes) {
                log.debug("Cached deprecation for $packageName has expired, removing it")
                state.deprecations.remove(packageName)
                return@let
            }
            return deprecationState.data
        } ?: log.debug("No cached deprecation found in cache for $packageName")

        // Check if the package is deprecated
        val comparatorVersion = Semver.coerce(comparator)?.version ?: "latest".also {
            log.warn("Couldn't coerce comparator $comparator to a version, using 'latest' instead")
        }
        val npmjsClient = NPMJSClient.getInstance(project)
        val reason = npmjsClient.getPackageDeprecation(packageName, comparatorVersion) ?: run {
            if (NUDSettingsState.instance.excludedUnmaintainedPackages
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    .contains(packageName)
            ) {
                log.debug("No deprecation found for $packageName, but it's excluded from unmaintained check")
                return null
            }
            if (NUDSettingsState.instance.unmaintainedDays == 0) {
                log.debug("No deprecation found for $packageName, unmaintained check is disabled")
                return null
            }
            log.debug("No deprecation found for $packageName, checking if it's unmaintained")
            val lastUpdate = npmjsClient.getPackageLastModified(packageName) ?: return null.also {
                log.warn("Couldn't get last modification date for $packageName")
            }
            try {
                val lastUpdateInstant = Instant.parse(lastUpdate)
                if (now > lastUpdateInstant + NUDSettingsState.instance.unmaintainedDays.days) {
                    log.debug("Package $packageName is unmaintained")
                    val timeDiff = lastUpdateInstant.periodUntil(now, TimeZone.currentSystemDefault())
                    return Deprecation(
                        Deprecation.Kind.UNMAINTAINED,
                        "This package looks unmaintained, it hasn't been updated in ${timeDiff.toReadableString()}. " + "Consider looking for an alternative.",
                        null
                    )
                }
            } catch (e: Exception) {
                log.warn("Couldn't parse last modification date for $packageName: $lastUpdate", e)
                return null
            }
            log.debug("Package $packageName is maintained")
            return null
        }

        if (npmjsClient.getPackageDeprecation(packageName) == null) {
            // Only the current version is deprecated, not the latest: suggest to upgrade instead
            log.debug("Only the current version of $packageName is deprecated, suggesting to upgrade")
            npmjsClient.getLatestVersion(packageName)?.let { Semver.coerce(it) }?.let { latestVersion ->
                val currentVersionText = Semver.coerce(comparatorVersion)?.let {
                    "$packageName ${it.major}"
                } ?: "The current version of $packageName"
                return Deprecation(
                    Deprecation.Kind.DEPRECATED,
                    "$currentVersionText is deprecated, consider upgrading to v${latestVersion.major}",
                    Deprecation.Replacement(packageName, latestVersion.version)
                )
            } ?: log.warn("Couldn't get latest version for $packageName or can't coerce it")
        }

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
                Deprecation.Replacement(potentialPackage, it)
            }
        }.filterNotNull().firstOrNull()

        return Deprecation(
            Deprecation.Kind.DEPRECATED,
            reason,
            replacementPackage
        )
    }
}
