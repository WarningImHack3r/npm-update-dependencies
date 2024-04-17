package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.backend.data.ScanResult
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.NUDHelper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.semver4j.Semver

@Service(Service.Level.PROJECT)
class PackageUpdateChecker(private val project: Project) {
    companion object {
        private val log = logger<PackageUpdateChecker>()

        @JvmStatic
        fun getInstance(project: Project): PackageUpdateChecker = project.service()
    }

    private fun isVersionUpgradable(version: String): Boolean {
        return !(version.startsWith("http")
                || version.startsWith("git")
                || version.contains("/")
                || !version.any { it.isDigit() })
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
        return if (versions.latest.satisfies(comparator)) {
            versions.satisfies == null
        } else {
            versions.satisfies != null
                    && versions.satisfies.satisfies(comparator)
                    && isVersionMoreRecentThanComparator(versions.satisfies, comparator)
        } && isVersionMoreRecentThanComparator(versions.latest, comparator)
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

    fun areUpdatesAvailable(packageName: String, comparator: String): ScanResult? {
        log.info("Checking for updates for $packageName with comparator $comparator")
        val availableUpdates = NUDState.getInstance(project).availableUpdates
        if (!isVersionUpgradable(comparator)) {
            if (availableUpdates.containsKey(packageName)) {
                availableUpdates.remove(packageName)
            }
            log.warn("Comparator $comparator is not upgradable, removing cached versions for $packageName")
            return null
        }

        // Check if an update has already been found
        availableUpdates[packageName]?.let { cachedVersions ->
            if (areVersionsMatchingComparatorNeeds(cachedVersions.versions, comparator)) {
                log.info("Cached versions for $packageName are still valid, returning them")
                return cachedVersions
            }
        }

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
            if (availableUpdates.containsKey(packageName)) {
                availableUpdates.remove(packageName)
            }
            log.info("No update available for $packageName, removing cached versions")
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
            val allVersions = npmjsClient.getAllVersions(packageName)?.mapNotNull { version ->
                Semver.coerce(version)
            }?.sortedDescending() ?: emptyList()

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

        return ScanResult(
            Versions(newestVersion, satisfyingVersion),
            filtersAffectingVersions
        ).also {
            log.info("Found updates for $packageName: $it")
            availableUpdates[packageName] = it
        }
    }
}
