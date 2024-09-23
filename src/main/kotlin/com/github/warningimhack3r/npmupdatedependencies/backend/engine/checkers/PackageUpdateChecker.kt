package com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Update
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NPMJSClient
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.NUDHelper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.semver4j.Semver

@Service(Service.Level.PROJECT)
class PackageUpdateChecker(private val project: Project) : PackageChecker() {
    companion object {
        private val log = logger<PackageUpdateChecker>()

        @JvmStatic
        fun getInstance(project: Project): PackageUpdateChecker = project.service()
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
            return null
        }

        // Check if an update has already been found
        state.availableUpdates[packageName]?.data?.let { update ->
            log.debug("Update found in cache for $packageName: $update")
            if (areVersionsMatchingComparatorNeeds(update.versions, comparator)) {
                log.info("Cached versions for $packageName are still valid, returning them")
                return update
            }
            log.debug("Cached versions for $packageName are outdated, removing them")
            state.availableUpdates.remove(packageName)

        } ?: log.warn("No cached versions found in cache for $packageName")

        // Check if an update is available
        val npmjsClient = NPMJSClient.getInstance(project)
        var newestVersion = npmjsClient.getLatestVersion(packageName)?.let {
            Semver.coerce(it)
        } ?: return null.also {
            log.warn("No latest version found for $packageName")
        }
        var satisfyingVersion: Semver? = null
        val updateAvailable = isVersionMoreRecentThanComparator(newestVersion, comparator)
        if (!updateAvailable) {
            log.info("No update available for $packageName")
            return null
        }

        // Check if the latest version is excluded, is a beta or doesn't satisfy the comparator
        val filtersAffectingVersions = mutableSetOf<String>()
        if (getVersionExcludingFilter(packageName, newestVersion) != null
            || newestVersion.preRelease.isNotEmpty()
            || newestVersion.build.isNotEmpty()
            || !newestVersion.satisfies(comparator)
        ) {
            log.debug("Latest version $newestVersion is excluded, a beta, or does not satisfy the comparator")
            val allVersions = try {
                npmjsClient.getAllVersions(packageName)?.mapNotNull { version ->
                    Semver.coerce(version)
                }?.sortedDescending() ?: emptyList()
            } catch (e: Exception) {
                log.warn("Failed to get all versions for $packageName", e)
                emptyList()
            }

            // Downgrade the latest version if it's filtered out
            var latest: Semver? = null
            for (version in allVersions) {
                val filter = getVersionExcludingFilter(packageName, version)
                if (filter != null) {
                    filtersAffectingVersions.add(filter)
                } else if (version.preRelease.isEmpty() && version.build.isEmpty()
                    && isVersionMoreRecentThanComparator(version, comparator)
                ) {
                    log.debug("Found latest version $version that satisfies the comparator, excluding filters: $filtersAffectingVersions")
                    latest = version
                    break
                }
            }
            newestVersion = latest ?: return null.also { // No version greater than the comparator and not filtered
                log.warn("No latest version found for $packageName that satisfies the comparator")
            }

            // Find satisfying version
            if (!newestVersion.satisfies(comparator)) {
                satisfyingVersion = allVersions.firstOrNull { version ->
                    val filter = getVersionExcludingFilter(packageName, version)
                    if (filter != null) {
                        filtersAffectingVersions.add(filter)
                    }
                    version != newestVersion && filter == null
                            && version.satisfies(comparator)
                            && isVersionMoreRecentThanComparator(version, comparator)
                }
                log.debug("Found satisfying version $satisfyingVersion for $packageName, excluding filters: $filtersAffectingVersions")
            }
        }

        return Update(
            Versions(newestVersion, satisfyingVersion),
            filtersAffectingVersions
        )
    }
}
