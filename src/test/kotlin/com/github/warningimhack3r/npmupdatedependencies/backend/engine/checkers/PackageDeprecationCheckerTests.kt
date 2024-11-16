package com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers

import com.github.warningimhack3r.npmupdatedependencies.NUDConstants.NPMJS_REGISTRY
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NPMJSClient
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Deprecation
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PackageDeprecationCheckerTests : BasePlatformTestCase() {
    val getReplacementPackageMethod =
        PackageDeprecationChecker::class.java.getDeclaredMethod("getReplacementPackage", String::class.java)
            .apply { isAccessible = true }

    private fun c(project: Project) = PackageDeprecationChecker.getInstance(project)

    private fun getReplacementPackage(checker: PackageDeprecationChecker, reason: String): Deprecation.Replacement? {
        return getReplacementPackageMethod.invoke(checker, reason) as Deprecation.Replacement?
    }

    private fun assertNameEquals(expectedPackage: String?, reason: String) {
        if (expectedPackage != null) {
            NUDState.getInstance(project).packageRegistries[expectedPackage] = NPMJS_REGISTRY
            NPMJSClient.getInstance(project).cache.put(
                "$NPMJS_REGISTRY/$expectedPackage/latest",
                """{"version": "1.0.0"}"""
            )
        }
        assertEquals(expectedPackage, getReplacementPackage(c(project), reason)?.name)
    }

    fun testCheckReplacementPackage_NoPackage() {
        val reason = "This package is deprecated"
        assertNameEquals(null, reason)
    }

    fun testCheckReplacementPackage_WithPackage() {
        val reason = "This package is deprecated, use new-package instead"
        assertNameEquals("new-package", reason)
    }

    fun testCheckReplacementPackage_WithScopedPackage() {
        val reason = "This package is deprecated, use @new-scope/new-package instead"
        assertNameEquals("@new-scope/new-package", reason)
    }

    fun testCheckReplacementPackage_WithBackticks() {
        val reason = "This package is deprecated, use `newpackage` instead"
        assertNameEquals("newpackage", reason)
    }

    fun testCheckReplacementPackage_WithLink() {
        val reason = "This package is deprecated, use [new-package](https://example.com) instead"
        assertNameEquals("new-package", reason)
    }
}
