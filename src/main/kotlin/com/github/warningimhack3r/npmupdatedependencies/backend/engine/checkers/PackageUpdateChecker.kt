package com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NPMJSClient
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.filterNotNullValues
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Update
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Versions
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.NUDHelper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.datetime.Clock
import org.semver4j.Semver
import kotlin.time.Duration.Companion.minutes

@Service(Service.Level.PROJECT)
class PackageUpdateChecker(private val project: Project) : PackageChecker() {
    companion object {
        private val log = logger<PackageUpdateChecker>()
        private val LOWERCASE_WORD = Regex("^[a-z]+$")

        @JvmStatic
        fun getInstance(project: Project): PackageUpdateChecker = project.service()
    }

    private fun computeMatchingTag(packageName: String, comparator: String): Pair<Update.Channel, Semver>? {
        val allTags = NPMJSClient.getInstance(project).getAllTags(packageName) ?: return null
        val coercedComparator = Semver.coerce(comparator) ?: return null
        val sortedTagsPairs = allTags.mapValues { (_, v) ->
            Semver.coerce(v)
        }.filterNotNullValues().toList().sortedBy { (_, v) -> v }
        val (tag, version) = sortedTagsPairs.firstOrNull { (_, v) ->
            v.isGreaterThan(coercedComparator)
        } ?: return null
        return when (val rawChannel = tag) {
            Update.Channel.Latest.LATEST -> Update.Channel.Latest()
            else -> Update.Channel.Other(rawChannel)
        } to version
    }

    private fun isVersionMoreRecentThanComparator(version: Semver, comparator: String): Boolean {
        return comparator.split(" ").any { comp ->
            val comparatorVersion = NUDHelper.Regex.semverPrefix.replace(comp, "")
            if (comparatorVersion.trim().isEmpty()) return@any false
            Semver.coerce(comparatorVersion)?.let { version.isGreaterThan(it) }.also {
                when (it) {
                    true -> log.debug("Version $version is greater than $comparatorVersion")
                    false -> log.debug("Version $version is not greater than $comparatorVersion")
                    else -> log.warn("Comparator version $comparatorVersion is invalid (comparator: $comp)")
                }
            } == true
        }
    }

    private fun areVersionsMatchingComparatorNeeds(versions: Versions, comparator: String): Boolean {
        return isVersionMoreRecentThanComparator(versions.latest, comparator) && versions.satisfies?.let { satisfying ->
            satisfying.satisfies(comparator)
                    && isVersionMoreRecentThanComparator(satisfying, comparator)
        } != false
    }

    private fun getVersionExcludingFilter(packageName: String, version: Semver): String? {
        return NUDSettingsState.instance.excludedVersions[packageName]?.let { excludedVersions ->
            log.debug("Excluded versions for $packageName: $excludedVersions")
            excludedVersions.firstOrNull { excludedVersion ->
                version.satisfies(excludedVersion).also { satisfies ->
                    if (satisfies) {
                        log.debug("Version $version satisfies excluded version $excludedVersion")
                    } else {
                        log.debug("Version $version does not satisfy excluded version $excludedVersion")
                    }
                }
            }
        }
    }

    fun checkAvailableUpdates(packageName: String, comparator: String): Update? {
        log.info("Checking for updates for $packageName with comparator $comparator")
        val state = NUDState.getInstance(project)
        if (!isComparatorUpgradable(comparator)) {
            log.warn("Comparator $comparator is not upgradable")
            if (state.availableUpdates.containsKey(packageName)) {
                log.debug("Removing cached versions for $packageName")
                state.availableUpdates.remove(packageName)
            }
            if (!NUDSettingsState.instance.suggestReplacingTags) {
                log.debug("Suggesting replacing tags is disabled")
                return null
            }
            // Tag comparator, this should be avoided
            with(comparator.lowercase()) {
                when {
                    LOWERCASE_WORD.matches(this) -> {
                        log.debug("Comparator $comparator is a tag, fetching version from tag")
                        val versionFromTag = NPMJSClient.getInstance(project).getVersionFromTag(
                            packageName, this
                        )
                        val potentialVersion = Semver.coerce(versionFromTag) ?: return null.also {
                            log.warn("Failed to coerce version $versionFromTag for $packageName")
                        }
                        log.debug("Version from tag $versionFromTag for $packageName")
                        return Update(Versions(potentialVersion))
                    }

                    equals("*") || isEmpty() -> {
                        log.debug("Comparator $comparator is a wildcard, fetching latest version")
                        val latestVersion =
                            NPMJSClient.getInstance(project).getVersionFromTag(packageName, "latest")?.let {
                                Semver.coerce(it)
                            } ?: return null.also {
                                log.warn("No latest version found for $packageName with wildcard comparator")
                            }
                        log.debug("Latest version $latestVersion for $packageName")
                        return Update(Versions(latestVersion))
                    }

                    else -> log.debug("Comparator $comparator is not a tag or wildcard, leaving")
                }
            }
            return null
        }

        // Check if an update has already been found
        state.availableUpdates[packageName]?.let { updateState ->
            log.debug("Update found in cache for $packageName: $updateState")
            if (updateState.comparator != comparator) {
                log.debug("Comparator for $packageName has changed, removing cached versions")
                state.availableUpdates.remove(packageName)
                return@let
            }
            if (Clock.System.now() >
                updateState.addedAt + NUDSettingsState.instance.cacheDurationMinutes.minutes
            ) {
                log.debug("Cached versions for $packageName have expired, removing them")
                state.availableUpdates.remove(packageName)
                return@let
            }
            val (_, realComparator) = getRealPackageAndValue(packageName, comparator)
            if (updateState.data == null || areVersionsMatchingComparatorNeeds(
                    updateState.data.versions,
                    realComparator
                )
            ) {
                log.info("Cached versions for $packageName are still valid, returning them")
                return updateState.data
            }
            log.debug("Cached versions for $packageName are not valid, removing them")
            state.availableUpdates.remove(packageName)
        } ?: log.warn("No cached versions found in cache for $packageName")

        // Check if an update is available
        val npmjsClient = NPMJSClient.getInstance(project)
        val (realPackageName, realComparator) = getRealPackageAndValue(packageName, comparator)
        if (realPackageName != packageName) {
            log.debug("Real package name for $packageName is $realPackageName (comparator: $realComparator)")
        }
        var (channel, newestVersion) = computeMatchingTag(realPackageName, realComparator) ?: return null.also {
            log.warn("No latest version found for $packageName (real: $realPackageName)")
        }
        var satisfyingVersion: Semver? = null
        val updateAvailable = isVersionMoreRecentThanComparator(newestVersion, realComparator)
        if (!updateAvailable) {
            log.info("No update available for $packageName")
            return null
        }

        // Check if the latest version is excluded, is a beta or doesn't satisfy the comparator
        val filtersAffectingVersions = mutableSetOf<String>()
        if (getVersionExcludingFilter(realPackageName, newestVersion) != null
            || newestVersion.preRelease.isNotEmpty()
            || newestVersion.build.isNotEmpty()
            || !newestVersion.satisfies(realComparator)
        ) {
            log.debug("Latest version $newestVersion is excluded, a beta, or does not satisfy the comparator")
            val allVersions = try {
                npmjsClient.getAllVersions(realPackageName)?.mapNotNull { version ->
                    Semver.coerce(version)
                }?.sortedDescending() ?: emptyList()
            } catch (e: Exception) {
                log.warn("Failed to get all versions for $realPackageName", e)
                emptyList()
            }

            // Downgrade the latest version if it's filtered out
            var latest: Semver? = null
            for (version in allVersions) {
                val filter = getVersionExcludingFilter(realPackageName, version)
                if (filter != null) {
                    filtersAffectingVersions.add(filter)
                } else if (version.preRelease.isEmpty() && version.build.isEmpty()
                    && isVersionMoreRecentThanComparator(version, realComparator)
                ) {
                    log.debug("Found latest version $version that satisfies the comparator, excluding filters: $filtersAffectingVersions")
                    latest = version
                    break
                }
            }
            newestVersion = latest ?: return null.also { // No version greater than the comparator and not filtered
                log.warn("No latest version found for $realPackageName that satisfies the comparator")
            }

            // Find satisfying version
            if (!newestVersion.satisfies(realComparator)) {
                satisfyingVersion = allVersions.firstOrNull { version ->
                    val filter = getVersionExcludingFilter(realPackageName, version)
                    if (filter != null) {
                        filtersAffectingVersions.add(filter)
                    }
                    version != newestVersion && filter == null
                            && version.satisfies(realComparator)
                            && isVersionMoreRecentThanComparator(version, realComparator)
                }
                log.debug("Found satisfying version $satisfyingVersion for $realPackageName, excluding filters: $filtersAffectingVersions")
            }
        }

        return Update(
            Versions(newestVersion, satisfyingVersion),
            channel,
            filtersAffectingVersions
        )
    }
}
