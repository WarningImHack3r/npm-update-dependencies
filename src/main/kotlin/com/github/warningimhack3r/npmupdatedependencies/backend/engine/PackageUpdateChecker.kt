package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.NUDHelper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.semver4j.Semver

@Service(Service.Level.PROJECT)
class PackageUpdateChecker(private val project: Project) {
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
            Semver.coerce(comparatorVersion)?.let { version.isGreaterThan(it) } == true
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

    private fun isVersionExcluded(packageName: String, version: Semver): Boolean {
        return NUDSettingsState.instance.excludedVersions[packageName]?.any { version.satisfies(it) } == true
    }

    fun areUpdatesAvailable(packageName: String, comparator: String): Versions? {
        val availableUpdates = project.service<NUDState>().availableUpdates
        if (!isVersionUpgradable(comparator)) {
            if (availableUpdates.containsKey(packageName)) {
                availableUpdates.remove(packageName)
            }
            return null
        }

        // Check if an update has already been found
        availableUpdates[packageName]?.let { cachedVersions ->
            if (areVersionsMatchingComparatorNeeds(cachedVersions, comparator)) {
                return cachedVersions
            }
        }

        // Check if an update is available
        val npmjsClient = project.service<NPMJSClient>()
        var newestVersion = npmjsClient.getLatestVersion(packageName)?.let {
            Semver.coerce(it)
        } ?: return null
        var satisfyingVersion: Semver? = null
        val updateAvailable = isVersionMoreRecentThanComparator(newestVersion, comparator)
        if (!updateAvailable) {
            if (availableUpdates.containsKey(packageName)) {
                availableUpdates.remove(packageName)
            }
            return null
        }

        // Check if the latest version is excluded or doesn't satisfy the comparator
        if (isVersionExcluded(packageName, newestVersion) || !newestVersion.satisfies(comparator)) {
            val allVersions = npmjsClient.getAllVersions(packageName)?.mapNotNull { version ->
                Semver.coerce(version)
            }?.sortedDescending() ?: emptyList()

            // Downgrade the latest version if it's filtered out
            for (version in allVersions) {
                if (!isVersionExcluded(packageName, newestVersion)
                    && isVersionMoreRecentThanComparator(version, comparator)
                ) {
                    newestVersion = version
                    break
                }
            }

            // Find satisfying version
            if (!newestVersion.satisfies(comparator)) {
                satisfyingVersion = allVersions.firstOrNull { version ->
                    version.satisfies(comparator)
                            && isVersionMoreRecentThanComparator(version, comparator)
                            && version != newestVersion
                            && !isVersionExcluded(packageName, version)
                }
            }
        }

        return Versions(newestVersion, satisfyingVersion).also {
            availableUpdates[packageName] = it
        }
    }
}
