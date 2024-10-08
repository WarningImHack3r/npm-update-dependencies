package com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers

abstract class PackageChecker {
    companion object {
        private val ONLY_DIGIT_X_OR_DOT = Regex("^[\\dx.]+$")
    }

    /**
     * Checks if the given version is upgradable.
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
                // If the version is "static", upgradable only if it includes (ends with?) a `x` or `X`
                ONLY_DIGIT_X_OR_DOT.matches(this) -> lowercase().contains("x")
                // Symbols prefixes
                startsWith("<") || startsWith("<=") -> false
                // tags
                none { it.isDigit() } -> false
                // URLs
                startsWith("http") || startsWith("git") -> false
                // GitHub URLs or local paths
                contains("/") -> false
                // NPM aliases
                startsWith("npm:") -> false // TODO: support in the future
                // Anything else is considered upgradable by default
                else -> true
            }
        }
    }
}
