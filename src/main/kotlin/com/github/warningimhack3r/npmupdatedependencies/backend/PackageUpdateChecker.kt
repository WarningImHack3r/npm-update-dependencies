package com.github.warningimhack3r.npmupdatedependencies.backend

import com.github.warningimhack3r.npmupdatedependencies.ui.helper.NUDHelper
import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.Semver.SemverType

object PackageUpdateChecker {
    val availableUpdates = mutableMapOf<String, Versions>()
    val deprecations = mutableMapOf<String, Deprecation>()

    private fun isVersionUpgradable(version: String): Boolean {
        if (version.startsWith("http")
            || version.startsWith("git")
            || version.contains("/")
            || !version.any { it.isDigit() }) return false
        return true
    }

    private fun isVersionMoreRecentThanComparator(version: String, comparator: String): Boolean {
        return comparator.split(" ").any { comp ->
            val comparatorVersion = NUDHelper.Regex.semverPrefix.replace(comp, "")
            if (comparatorVersion.trim().isEmpty()) return@any false
            Semver(version, SemverType.NPM).isGreaterThan(comparatorVersion)
        }
    }

    fun hasUpdateAvailable(name: String, currentComparator: String): Pair<Boolean, Versions?> {
        // Check if an update has already been found
        if (availableUpdates.containsKey(name) && isVersionMoreRecentThanComparator(availableUpdates[name]!!.latest, currentComparator)) {
            return Pair(true, availableUpdates[name])
        }

        // Check if current version is an upgradable version
        if (!isVersionUpgradable(currentComparator)) return Pair(false, null)

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
                            && !currentComparator.contains(version.originalValue)
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
