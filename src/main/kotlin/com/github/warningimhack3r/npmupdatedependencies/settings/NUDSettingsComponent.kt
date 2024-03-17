package com.github.warningimhack3r.npmupdatedependencies.settings

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.github.warningimhack3r.npmupdatedependencies.ui.statusbar.StatusBarMode
import com.intellij.ide.DataManager
import com.intellij.openapi.options.ex.Settings
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*

class NUDSettingsComponent {
    val panel = panel {
        val settings = NUDSettingsState()
        group("Annotation Actions") {
            row("Default update type:") {
                comboBox(enumValues<Versions.Kind>().toList())
                    .bindItem(settings::defaultUpdateType.toMutableProperty())
            }
            row("Default deprecation action:") {
                comboBox(enumValues<Deprecation.Action>().toList())
                    .bindItem(settings::defaultDeprecationAction.toMutableProperty())
            }
        }
        group("Deprecations") {
            row {
                checkBox("Show deprecation banner")
                    .comment("Show a warning banner when at least one dependency is deprecated.")
                    .bindSelected(settings::showDeprecationBanner)
            }
            row {
                checkBox("Automatically reorder dependencies")
                    .comment("Reorder dependencies after replacing deprecated ones.<br>Useful when a new dependency starts with a different letter than the old one.")
                    .bindSelected(settings::autoReorderDependencies)
            }
        }
        group("Status Bar") {
            lateinit var statusBarEnabled: Cell<JBCheckBox>
            row {
                statusBarEnabled = checkBox("Show status bar widget")
                    .comment("Show a widget in the status bar that shows the scan status, the number of outdated dependencies, and allows you to open them to update them.")
                    .bindSelected(settings::showStatusBarWidget)
            }
            indent {
                row("Status Bar mode:") {
                    comboBox(enumValues<StatusBarMode>().toList())
                        .comment("Compact mode only shows \"U\" for outdated dependencies and \"D\" for deprecated dependencies.")
                        .bindItem(settings::statusBarMode.toMutableProperty())
                }.enabledIf(statusBarEnabled.selected)
            }
        }
        group("Auto-Fix") {
            row {
                checkBox("Auto-fix on save")
                    .comment("Auto-fix applies the default update type and deprecation action to all dependencies when saving <code>package.json</code>.")
                    .bindSelected(settings::autoFixOnSave)
            }
        }

        row {
            label("Plugin's keyboard shortcuts can be changed in keymap settings.")
            link("Go to keymap settings") {
                DataManager.getInstance().dataContextFromFocusAsync.onSuccess { dataContext ->
                    val jbSettings = Settings.KEY.getData(dataContext) ?: return@onSuccess
                    jbSettings.select(jbSettings.find("preferences.keymap"))
                }
            }
        }
        group("Links") {
            row {
                browserLink("Source code", "https://github.com/WarningImHack3r/npm-update-dependencies")
                browserLink("Plugin homepage", "https://plugins.jetbrains.com/plugin/21105-npm-update-dependencies")
                browserLink("Issue tracker", "https://github.com/WarningImHack3r/npm-update-dependencies/issues")
                browserLink("Rate plugin", "https://plugins.jetbrains.com/plugin/21105-npm-update-dependencies/reviews")
            }
        }
    }
}
