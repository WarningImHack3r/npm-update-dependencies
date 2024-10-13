package com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PackageCheckerTests : BasePlatformTestCase() {
    companion object {
        private const val DEFAULT_PACKAGE_NAME = "something"
    }

    inner class TestChecker : PackageChecker() {
        fun realPnVFromValue(comparator: String) =
            getRealPackageAndValue(DEFAULT_PACKAGE_NAME, comparator)
    }

    private val checker = TestChecker()

    fun testRealPackageComboBaseComparator() {
        val (packageName, comparator) = checker.realPnVFromValue("^16.8.0")
        assertEquals(DEFAULT_PACKAGE_NAME, packageName)
        assertEquals("^16.8.0", comparator)
    }

    fun testRealPackageComboNPMComparator() {
        val (packageName, comparator) = checker.realPnVFromValue("npm:actually@^1.2.3")
        assertEquals("actually", packageName)
        assertEquals("^1.2.3", comparator)
    }

    fun testRealPackageComboNPMComparatorWithScope() {
        val (packageName, comparator) = checker.realPnVFromValue("npm:@actually/subactually@1.2.3")
        assertEquals("@actually/subactually", packageName)
        assertEquals("1.2.3", comparator)
    }

    fun testRealPackageComboNPMComparatorWithScope2() {
        val (packageName, comparator) = checker.realPnVFromValue("npm:@actually/subactually@>=1.2.3")
        assertEquals("@actually/subactually", packageName)
        assertEquals(">=1.2.3", comparator)
    }

    fun testNoAtSign() {
        val (packageName, comparator) = checker.realPnVFromValue("npm:actually")
        assertEquals(DEFAULT_PACKAGE_NAME, packageName)
        assertEquals("npm:actually", comparator)
    }

    fun testNoAtSign2() {
        val (packageName, comparator) = checker.realPnVFromValue("npm:@actually/subactually")
        assertEquals(DEFAULT_PACKAGE_NAME, packageName)
        assertEquals("npm:@actually/subactually", comparator)
    }

    fun testInvalidValue() {
        val (packageName, comparator) = checker.realPnVFromValue("npm:actually@")
        assertEquals(DEFAULT_PACKAGE_NAME, packageName)
        assertEquals("npm:actually@", comparator)
    }

    fun testInvalidName() {
        val (packageName, comparator) = checker.realPnVFromValue("npm:@")
        assertEquals(DEFAULT_PACKAGE_NAME, packageName)
        assertEquals("npm:@", comparator)
    }

    fun testInvalidColonNotation() {
        val (packageName, comparator) = checker.realPnVFromValue("actually:1.2.3")
        assertEquals(DEFAULT_PACKAGE_NAME, packageName)
        assertEquals("actually:1.2.3", comparator)
    }
}
