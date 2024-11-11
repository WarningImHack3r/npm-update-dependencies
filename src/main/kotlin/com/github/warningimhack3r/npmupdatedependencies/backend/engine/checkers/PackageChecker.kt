package com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers

import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState

abstract class PackageChecker {
    companion object {
        private val ONLY_DIGIT_X_OR_DOT = Regex("^[\\dx.]+$")
    }

    /**
     * Checks if the given comparator is supported.
     * Supported means that the comparator is a processable version.
     *
     * @param comparator The comparator to check.
     * @return `true` if the comparator is supported, `false` otherwise.
     */
    protected fun isComparatorSupported(comparator: String): Boolean {
        return with(comparator) {
            when {
                // tags
                none { it.isDigit() } -> false
                // URLs
                startsWith("http") || startsWith("git") -> false
                // GitHub URLs or local paths
                contains("/") -> false
                // Anything else is considered supported by default
                else -> true
            }
        }
    }

    /**
     * Checks if the given version is upgradable.
     * Upgradable means that the version can be upgraded to a newer version.
     *
     * @param comparator The comparator to check.
     * @return `true` if the comparator is upgradable, `false` otherwise.
     */
    protected fun isComparatorUpgradable(comparator: String): Boolean {
        // Based on https://docs.npmjs.com/cli/v10/configuring-npm/package-json#dependencies
        // Using a blacklist approach, as it's easier to get right, and the Semver library
        // will handle the rest for us.
        return with(comparator) {
            when {
                // Anything that is not supported is not upgradable
                !isComparatorSupported(this) -> false
                // If the version is "static", upgradable only if it includes (ends with?) a `x` or `X`
                ONLY_DIGIT_X_OR_DOT.matches(this) -> lowercase().contains("x") || NUDSettingsState.instance.checkStaticComparators
                // Symbols prefixes
                startsWith("<") || startsWith("<=") -> false
                // Anything else is considered upgradable by default
                else -> true
            }
        }
    }

    /**
     * Gets the real package name and value from the given package name and comparator.
     * This is useful for packages having a `npm:` prefix.
     *
     * @param packageName The package name.
     * @param comparator The comparator.
     * @return A pair containing the real package name and value.
     */
    protected fun getRealPackageAndValue(packageName: String, comparator: String): Pair<String, String> {
        if (!comparator.startsWith("npm:")) {
            return packageName to comparator
        }
        val aliasLessValue = comparator.removePrefix("npm:")
        return if (aliasLessValue.startsWith("@")) {
            // Scoped package
            val pkg = "@${aliasLessValue.removePrefix("@").substringBeforeLast("@")}"
            val value = aliasLessValue.removePrefix(pkg).removePrefix("@")
            if (pkg.isNotEmpty() && value.isNotEmpty()) {
                pkg to value
            } else packageName to comparator
        } else aliasLessValue.split("@").let { split ->
            // Normal package
            if (split.size == 2) {
                val (pkg, value) = split
                if (pkg.isNotEmpty() && value.isNotEmpty()) {
                    pkg to value
                } else packageName to comparator
            } else packageName to comparator
        }
    }
}
