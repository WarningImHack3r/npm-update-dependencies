package com.github.warningimhack3r.npmupdatedependencies.ui.helpers

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NUDHelperTests : BasePlatformTestCase() {

    fun getPrefixValue(prefix: String) = NUDHelper.Regex.semverPrefix.find(prefix)?.value ?: ""

    fun testSemverPrefixNoPrefix() {
        assertEquals("", getPrefixValue("1.2.3"))
    }

    fun testSemverPrefixNoPrefixLargeVersion() {
        assertEquals("", getPrefixValue("15.0.0"))
    }

    fun testSemverPrefixCaret() {
        assertEquals("^", getPrefixValue("^1.2.3"))
    }

    fun testSemverPrefixMultiple() {
        assertEquals(">=", getPrefixValue(">=1.2.3"))
    }

    fun testSemverPrefixMultipleLargeVersion() {
        assertEquals(">=", getPrefixValue(">=125.0.0"))
    }

    fun testSemverPrefixAlias() {
        assertEquals("npm:vite@", getPrefixValue("npm:vite@1.2.3"))
    }

    fun testSemverPrefixAliasLargeVersion() {
        assertEquals("npm:vite@", getPrefixValue("npm:vite@15.0.0"))
    }

    fun testSemverPrefixAliasSymbol() {
        assertEquals("npm:vite@^", getPrefixValue("npm:vite@^1.2.3"))
    }

    fun testSemverPrefixAliasMultiple() {
        assertEquals("npm:vite@>=", getPrefixValue("npm:vite@>=1.2.3"))
    }

    fun testSemverPrefixAliasMultipleLargeVersion() {
        assertEquals("npm:vite@>=", getPrefixValue("npm:vite@>=125.0.0"))
    }

    fun testSemverPrefixAliasWithNumberMultipleLargeVersion() {
        assertEquals("npm:eslint-plugin-svelte3@>=", getPrefixValue("npm:eslint-plugin-svelte3@>=436.0.0"))
    }
}
