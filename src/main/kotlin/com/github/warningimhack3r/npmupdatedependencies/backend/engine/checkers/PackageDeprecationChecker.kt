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
import kotlinx.datetime.DateTimeArithmeticException
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
        private val STARTING_PUNCTUATION = Regex("^\\[+")
        private val ENDING_PUNCTUATION = Regex("[,;.!?:]+$")
        private val MARKDOWN_LINK_LINK_PART = Regex("]\\([^)]+\\)")

        @JvmStatic
        fun getInstance(project: Project): PackageDeprecationChecker = project.service()
    }

    private fun checkUnmaintainedPackage(packageName: String, realPackageName: String): Deprecation? {
        if (NUDSettingsState.instance.excludedUnmaintainedPackages
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .contains(packageName)
        ) {
            log.debug("No deprecation found for $packageName, but it's excluded from unmaintained check")
            return null
        }
        if (NUDSettingsState.instance.unmaintainedDays == 0) {
            log.debug("No deprecation found for $realPackageName, unmaintained check is disabled")
            return null
        }
        log.debug("No deprecation found for $realPackageName, checking if it's unmaintained")
        val lastUpdate = NPMJSClient.getInstance(project).getPackageLastModified(realPackageName) ?: return null.also {
            log.warn("Couldn't get last modification date for $realPackageName")
        }
        val lastUpdateInstant = try {
            Instant.parse(lastUpdate)
        } catch (e: IllegalArgumentException) {
            log.warn("Couldn't parse last modification date for $realPackageName: $lastUpdate", e)
            return null
        }
        val now = Clock.System.now()
        if (now > lastUpdateInstant + NUDSettingsState.instance.unmaintainedDays.days) {
            log.debug("Package $realPackageName is unmaintained")
            val timeDiff = try {
                lastUpdateInstant.periodUntil(now, TimeZone.currentSystemDefault())
            } catch (e: DateTimeArithmeticException) {
                log.warn("Couldn't calculate time difference for $realPackageName", e)
                return null
            }
            return Deprecation(
                Deprecation.Kind.UNMAINTAINED,
                "This package looks unmaintained, it hasn't been updated in ${timeDiff.toReadableString()}. "
                        + "Consider looking for an alternative.",
                null
            )
        }
        log.debug("Package $realPackageName is maintained")
        return null
    }

    private fun getReplacementPackage(reason: String): Deprecation.Replacement? {
        val npmjsClient = NPMJSClient.getInstance(project)
        return reason.replace(MARKDOWN_LINK_LINK_PART, "").split(" ").map { word ->
            word
                .replace(STARTING_PUNCTUATION, "")
                .replace(ENDING_PUNCTUATION, "")
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
                    // If it's between backticks and lowercase, it's likely a package name
                    startsWith("`") && endsWith("`") -> lowercase() == this
                    // Else if we're unsure, we don't consider it as a package name
                    else -> false
                }
            }
        }.parallelMap {
            val potentialPackage = it.removeSurrounding("`")
            // Confirm that the word is a package name by trying to get its latest version
            npmjsClient.getLatestVersion(potentialPackage)?.let { latestVersion ->
                Deprecation.Replacement(potentialPackage, latestVersion)
            }
        }.filterNotNull().firstOrNull()
    }

    fun getDeprecationStatus(packageName: String, comparator: String): Deprecation? {
        log.info("Checking for deprecations for $packageName with comparator $comparator")
        val state = NUDState.getInstance(project)
        if (!isComparatorSupported(comparator)) {
            log.warn("Comparator $comparator is not supported")
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
            if (Clock.System.now() > deprecationState.addedAt + NUDSettingsState.instance.cacheDurationMinutes.minutes) {
                log.debug("Cached deprecation for $packageName has expired, removing it")
                state.deprecations.remove(packageName)
                return@let
            }
            return deprecationState.data
        } ?: log.debug("No cached deprecation found in cache for $packageName")

        // Check if the package is deprecated
        val (realPackageName, realComparator) = getRealPackageAndValue(packageName, comparator)
        if (realPackageName != packageName) {
            log.debug("Real package name for $packageName is $realPackageName (comparator: $realComparator)")
        }
        val comparatorVersion = Semver.coerce(realComparator)?.version ?: "latest".also {
            log.warn("Couldn't coerce comparator $realComparator to a version, using 'latest' instead")
        }
        val npmjsClient = NPMJSClient.getInstance(project)
        val reason = npmjsClient.getPackageDeprecation(realPackageName, comparatorVersion)
            ?: return checkUnmaintainedPackage(packageName, realPackageName)

        if (comparatorVersion != "latest" && npmjsClient.getPackageDeprecation(realPackageName) == null) {
            // Only the current version is deprecated, not the latest: suggest to upgrade instead
            log.debug("Only the current version of $realPackageName is deprecated, suggesting to upgrade")
            npmjsClient.getLatestVersion(realPackageName)?.let { Semver.coerce(it) }?.let { latestVersion ->
                val currentVersionText = Semver.coerce(comparatorVersion)?.let {
                    "$realPackageName ${it.major}"
                } ?: "The current version of $realPackageName"
                return Deprecation(
                    Deprecation.Kind.DEPRECATED,
                    "$currentVersionText is deprecated, consider upgrading to v${latestVersion.major}",
                    Deprecation.Replacement(realPackageName, latestVersion.version)
                )
            } ?: log.warn("Couldn't get latest version for $realPackageName or can't coerce it")
        }

        return Deprecation(
            Deprecation.Kind.DEPRECATED,
            reason,
            getReplacementPackage(reason)
        )
    }
}
