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

    private fun isVersionMoreRecentThanComparator(version: String, comparator: String): Boolean {
        return comparator.split(" ").any { comp ->
            val comparatorVersion = NUDHelper.Regex.semverPrefix.replace(comp, "")
            if (comparatorVersion.trim().isEmpty()) return@any false
            Semver.coerce(comparatorVersion)?.let { Semver(version).isGreaterThan(it) } == true
        }
    }

    private fun areVersionsMatchingComparatorNeeds(versions: Versions, comparator: String): Boolean {
        return if (Semver(versions.latest).satisfies(comparator)) {
            versions.satisfies == null
        } else {
            versions.satisfies != null
                    && Semver(versions.satisfies).satisfies(comparator)
                    && isVersionMoreRecentThanComparator(versions.satisfies, comparator)
        } && isVersionMoreRecentThanComparator(versions.latest, comparator)
    }

    private fun isVersionExcluded(packageName: String, version: String): Boolean {
        return NUDSettingsState.instance.excludedVersions[packageName]?.any { Semver(version).satisfies(it) } == true
    }

    private fun filterExcludedVersions(packageName: String, versions: Versions): Versions? {
        // TODO: rework all that to find the most appropriate versions instead of just excluding them
        return if (versions.satisfies != null && isVersionExcluded(packageName, versions.satisfies)) {
            versions.copy(satisfies = null)
        } else if (isVersionExcluded(packageName, versions.latest)) {
            if (versions.satisfies != null) versions.copy(latest = versions.satisfies) else null
        } else versions
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
                return filterExcludedVersions(packageName, cachedVersions)
            }
        }

        // Check if update is available
        val npmjsClient = project.service<NPMJSClient>()
        val newVersion = npmjsClient.getLatestVersion(packageName) ?: return null
        val updateAvailable = isVersionMoreRecentThanComparator(newVersion, comparator)

        // Find satisfying version
        var satisfyingVersion: String? = null
        if (!Semver(newVersion).satisfies(comparator)) {
            val newVersionSemver = Semver(newVersion)
            satisfyingVersion = npmjsClient.getAllVersions(packageName)?.let { versions ->
                versions.map { version ->
                    Semver(version)
                }.filter { version ->
                    version.satisfies(comparator)
                            && isVersionMoreRecentThanComparator(version.version, comparator)
                            && version != newVersionSemver
                }.maxOrNull()?.version
            }
        }
        val versions = Versions(newVersion, satisfyingVersion)

        // Return appropriate values
        if (updateAvailable) {
            availableUpdates[packageName] = versions
        } else if (availableUpdates.containsKey(packageName)) {
            availableUpdates.remove(packageName)
        }
        return filterExcludedVersions(packageName, versions)
    }
}
