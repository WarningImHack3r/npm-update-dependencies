package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.availableUpdates
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.deprecations
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.github.warningimhack3r.npmupdatedependencies.ui.helpers.NUDHelper
import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.Semver.SemverType

object PackageUpdateChecker {
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
            Semver(version, SemverType.NPM).isGreaterThan(comparatorVersion)
        }
    }

    private fun areVersionsMatchingComparatorNeeds(versions: Versions, comparator: String): Boolean {
        return if (Semver(versions.latest, SemverType.NPM).satisfies(comparator)) {
            versions.satisfies == null
        } else {
            versions.satisfies != null
                    && Semver(versions.satisfies, SemverType.NPM).satisfies(comparator)
                    && isVersionMoreRecentThanComparator(versions.satisfies, comparator)
        } && isVersionMoreRecentThanComparator(versions.latest, comparator)
    }

    fun hasUpdateAvailable(name: String, currentComparator: String): Pair<Boolean, Versions?> {
        if (deprecations.containsKey(name) // Check if the dependency is not deprecated
            || !isVersionUpgradable(currentComparator)) { // Check if current version is an upgradable version
            if (availableUpdates.containsKey(name)) availableUpdates.remove(name)
            return Pair(false, null)
        }

        // Check if an update has already been found
        if (availableUpdates.containsKey(name)
            && areVersionsMatchingComparatorNeeds(availableUpdates[name]!!, currentComparator)) {
            return Pair(true, availableUpdates[name])
        }

        // Check if update is available
        val newVersion = NPMJSClient.getLatestVersion(name) ?: return Pair(false, null)
        val updateAvailable = isVersionMoreRecentThanComparator(newVersion, currentComparator)

        // Find satisfying version
        var satisfyingVersion: String? = null
        if (!Semver(newVersion, SemverType.NPM).satisfies(currentComparator)) {
            val newVersionSemver = Semver(newVersion, SemverType.NPM)
            satisfyingVersion = NPMJSClient.getAllVersions(name)?.let { versions ->
                versions.map { version ->
                    Semver(version, SemverType.NPM)
                }.filter { version ->
                    version.satisfies(currentComparator)
                            && isVersionMoreRecentThanComparator(version.originalValue, currentComparator)
                            && version != newVersionSemver
                }.maxOrNull()?.originalValue
            }
        }
        val versions = Versions(newVersion, satisfyingVersion)

        // Return appropriate values
        if (updateAvailable) {
            availableUpdates[name] = versions
        } else if (availableUpdates.containsKey(name)) {
            availableUpdates.remove(name)
        }
        return Pair(updateAvailable, versions)
    }
}
