package com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers

abstract class PackageChecker {
    
    protected fun isVersionUpgradable(version: String): Boolean {
        return !(version.startsWith("http")
                || version.startsWith("git")
                || version.contains("/")
                || !version.any { it.isDigit() })
    }
}
