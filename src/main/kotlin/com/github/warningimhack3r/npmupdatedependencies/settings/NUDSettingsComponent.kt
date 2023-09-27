@file:SuppressWarnings("kotlin:S1128") // Suppress "Unused imports should be removed" for Pair destructuring
package com.github.warningimhack3r.npmupdatedependencies.settings

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.intellij.ide.DataManager
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.containers.ContainerUtil.zip
import java.awt.Component
import javax.swing.JCheckBox
import javax.swing.JComboBox

class NUDSettingsComponent {
    var values: MutableMap<String, Any> = NUDSettingsState.instance.mapped.toMutableMap()
        set(newValues) {
            field = newValues
            // Update all components
            if (componentsList.size != newValues.size) {
                throw IllegalStateException("The number of components (${componentsList.size}) does not match the number of values (${newValues.size})")
            }
            zip(componentsList, newValues.values).forEach { (component, value) ->
                when (component) {
                    is JComboBox<*> -> component.selectedIndex = value as Int
                    is JCheckBox -> component.isSelected = value as Boolean
                }
            }
        }

    private var componentsList = mutableListOf<Component>()
    val panel = panel {
        group("Annotation Actions") {
            row("Default update type:") {
                comboBox(
                    Versions.Kind.values()
                    .map { version ->
                        version.text.replaceFirstChar { it.uppercase() }
                    })
                    .applyToComponent {
                        componentsList.add(this)
                        selectedIndex = values["defaultUpdateType"] as Int
                        addItemListener {
                            values["defaultUpdateType"] = selectedIndex
                        }
                    }
            }
            row("Default deprecation action:") {
                comboBox(Deprecation.Action.values().map { it.text })
                    .applyToComponent {
                        componentsList.add(this)
                        selectedIndex = values["defaultDeprecationAction"] as Int
                        addItemListener {
                            values["defaultDeprecationAction"] = selectedIndex
                        }
                    }
            }
        }
        group("Deprecations") {
            row {
                checkBox("Show deprecation banner")
                    .comment("Show a warning banner when at least one dependency is deprecated.")
                    .applyToComponent {
                        componentsList.add(this)
                        isSelected = values["showDeprecationBanner"] as Boolean
                        addItemListener {
                            values["showDeprecationBanner"] = isSelected
                        }
                    }
            }
            row {
                checkBox("Automatically reorder dependencies")
                    .comment("Reorder dependencies after replacing deprecated ones.<br>Useful when a new dependency starts with a different letter than the old one.")
                    .applyToComponent {
                        componentsList.add(this)
                        isSelected = values["autoReorderDependencies"] as Boolean
                        addItemListener {
                            values["autoReorderDependencies"] = isSelected
                        }
                    }
            }
        }
        group("Status Bar") {
            lateinit var statusBarEnabled: Cell<JBCheckBox>
            row {
                statusBarEnabled = checkBox("Show status bar widget")
                    .comment("Show a widget in the status bar that shows the scan status, the number of outdated dependencies, and allows you to open them to update them.")
                    .applyToComponent {
                        componentsList.add(this)
                        isSelected = values["showStatusBarWidget"] as Boolean
                        addItemListener {
                            values["showStatusBarWidget"] = isSelected
                        }
                    }
            }
            row("Status Bar mode:") {
                comboBox(listOf("Full", "Compact"))
                    .comment("Compact mode only shows \"U\" for outdated dependencies and \"D\" for deprecated dependencies.")
                    .applyToComponent {
                        componentsList.add(this)
                        selectedIndex = values["statusBarMode"] as Int
                        addItemListener {
                            values["statusBarMode"] = selectedIndex
                        }
                    }
            }.enabledIf(statusBarEnabled.selected)
        }
        group("Auto-Fix") {
            row {
                checkBox("Auto-fix on save")
                    .comment("Auto-fix applies the default update type and deprecation action to all dependencies when saving <code>package.json</code>.")
                    .applyToComponent {
                        componentsList.add(this)
                        isSelected = values["autoFixOnSave"] as Boolean
                        addItemListener {
                            values["autoFixOnSave"] = isSelected
                        }
                    }
            }
        }
        row {
            label("Plugin's keyboard shortcuts can be changed in keymap settings.")
            link("Go to keymap settings") {
                DataManager.getInstance().dataContextFromFocusAsync.onSuccess { dataContext ->
                    val settings = Settings.KEY.getData(dataContext) ?: return@onSuccess
                    settings.select(settings.find("preferences.keymap"))
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
