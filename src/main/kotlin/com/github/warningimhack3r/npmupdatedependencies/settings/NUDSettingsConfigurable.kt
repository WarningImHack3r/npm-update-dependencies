package com.github.warningimhack3r.npmupdatedependencies.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class NUDSettingsConfigurable : Configurable {
    private val settingsView: NUDSettingsComponent by lazy { NUDSettingsComponent() }
    override fun createComponent(): JComponent = settingsView.panel

    override fun isModified(): Boolean = NUDSettingsState.instance.mapped != settingsView.values

    override fun apply() {
        NUDSettingsState.instance.mapped = settingsView.values
    }

    override fun reset() {
        settingsView.values = NUDSettingsState.instance.mapped.toMutableMap()
    }

    override fun getDisplayName() = "NPM Update Dependencies"
}
