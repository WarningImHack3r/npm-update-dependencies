package com.github.warningimhack3r.npmupdatedependencies.settings

import com.intellij.openapi.options.Configurable

class NUDSettingsConfigurable : Configurable {
    private val settingsPanel by lazy { NUDSettingsComponent().panel }

    override fun createComponent() = settingsPanel

    override fun isModified() = settingsPanel.isModified()

    override fun apply() = settingsPanel.apply()

    override fun reset() = settingsPanel.reset()

    override fun getDisplayName() = "NPM Update Dependencies"
}
