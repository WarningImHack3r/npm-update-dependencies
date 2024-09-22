package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.data.DeprecationState
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.parallelMap
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class PackageDeprecationChecker(private val project: Project) {
    companion object {
        private val log = logger<PackageDeprecationChecker>()

        @JvmStatic
        fun getInstance(project: Project): PackageDeprecationChecker = project.service()
    }

    fun getDeprecationStatus(packageName: String): Deprecation? {
        val npmjsClient = NPMJSClient.getInstance(project)

        // Check if a deprecation has already been found
        NUDState.getInstance(project).deprecations[packageName]?.let { deprecationState ->
            when (deprecationState) {
                is DeprecationState.Deprecated -> {
                    log.debug("Deprecation found in cache for $packageName: ${deprecationState.deprecation}")
                    return deprecationState.deprecation
                }

                else -> log.debug("No deprecation found in cache: $packageName")
            }
        }

        // Check if the package is deprecated
        val reason = npmjsClient.getPackageDeprecation(packageName) ?: return null

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
                Pair(potentialPackage, it)
            }
        }.filterNotNull().firstOrNull()

        return replacementPackage?.let { (name, version) ->
            // We found a package name and its latest version, so we can create a replacement
            Deprecation(reason, Deprecation.Replacement(name, version))
        } ?: Deprecation(reason, null)
    }
}
