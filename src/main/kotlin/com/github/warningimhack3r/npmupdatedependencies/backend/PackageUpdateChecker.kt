package com.github.warningimhack3r.npmupdatedependencies.backend

import com.github.warningimhack3r.npmupdatedependencies.ui.helper.NUDHelper
import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.Semver.SemverType

object PackageUpdateChecker {
    val availableUpdates = mutableMapOf<String, Versions>()

    private fun isVersionUpgradable(version: String): Boolean {
        if (version.startsWith("http")) return false
        if (version.startsWith("git")) return false
        if (version.contains("/")) return false
        if (!version.any { it.isDigit() }) return false
        return true
    }

    fun hasUpdateAvailable(name: String, currentComparator: String): Pair<Boolean, Versions?> {
        // Check if current version is an upgradable version
        if (!isVersionUpgradable(currentComparator)) return Pair(false, null)

        // Check if update is available
        val newVersion = NPMJSClient.getLatestVersion(name)
        val updateAvailable = currentComparator.split(" ").any { comparator ->
            val comparatorVersion = NUDHelper.Regex.semverPrefix.replace(comparator, "")
            if (comparatorVersion.trim().isEmpty()) return@any false
            Semver(newVersion, SemverType.NPM).isGreaterThan(comparatorVersion)
        }

        // Find satisfying version
        var satisfyingVersion: String? = null
        if (!Semver(newVersion, SemverType.NPM).satisfies(currentComparator)) {
            val newVersionSemver = Semver(newVersion, SemverType.NPM)
            satisfyingVersion = NPMJSClient.getAllVersions(name).map { version ->
                Semver(version, SemverType.NPM)
            }.filter { version ->
                version.satisfies(currentComparator) && version != newVersionSemver
            }.maxOrNull()!!.originalValue
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
