package com.github.warningimhack3r.npmupdatedependencies.settings

import com.github.warningimhack3r.npmupdatedependencies.backend.models.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Versions
import com.github.warningimhack3r.npmupdatedependencies.ui.statusbar.StatusBarMode
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

class NUDSettingsComponent {
    private data class ExcludedVersion(
        var packageName: String,
        var versions: List<String>
    )

    private class CellRenderer : DefaultTableCellRenderer.UIResource() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            return super.getTableCellRendererComponent(table, value, isSelected, false, row, column)
        }
    }

    private fun excludedVersionsPanel(
        excludedVersions: Map<String, List<String>>,
        newVersions: (Map<String, List<String>>) -> Unit
    ): DialogBuilder = DialogBuilder(panel).apply {
        val model = ListTableModel<ExcludedVersion>(
            object : ColumnInfo<ExcludedVersion, String>("Package") {
                override fun getRenderer(item: ExcludedVersion?) = CellRenderer()

                override fun valueOf(item: ExcludedVersion?) = item?.packageName

                override fun setValue(item: ExcludedVersion?, value: String?) {
                    if (item != null && value != null) item.packageName = value.trim()
                }

                override fun isCellEditable(item: ExcludedVersion?) = item != null
            },
            object : ColumnInfo<ExcludedVersion, String>("Excluded Versions") {
                override fun getRenderer(item: ExcludedVersion?) = CellRenderer()

                override fun valueOf(item: ExcludedVersion?) = item?.versions?.joinToString(",")

                override fun setValue(item: ExcludedVersion?, value: String?) {
                    if (item != null && value != null) {
                        item.versions = value.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    }
                }

                override fun isCellEditable(item: ExcludedVersion?) = item != null
            }
        ).apply {
            addRows(excludedVersions.map { ExcludedVersion(it.key, it.value) })
        }
        val table = TableView(model).apply {
            visibleRowCount = 8
            rowSelectionAllowed = false
            tableHeader.reorderingAllowed = false

            setExpandableItemsEnabled(false)
        }
        val content = panel {
            row {
                text(
                    "This table is a list of versions that should be excluded from updates.<br>The left column is the package name, and the right column is a comma-separated list of version patterns to exclude.<br>Use <code>*</code> to exclude all versions.<br><br>Example: a value of <code>1.2.3,2.*</code> excludes the versions <code>1.2.3</code>, as well as all versions starting with <code>2.</code>.<br>As such, specifying a value as <code>*</code> effectively excludes all versions of the package.<br>For wildcard patterns like this, <code>*</code>, <code>x</code>, and <code>X</code> are supported and have the same meaning."
                )
            }
            row {
                cell(
                    ToolbarDecorator.createDecorator(table)
                        .setAddAction {
                            model.addRow(ExcludedVersion("", emptyList()))
                        }
                        .setRemoveAction {
                            model.removeRow(table.selectedRow)
                        }
                        .disableUpDownActions()
                        .createPanel()
                )
                    .align(AlignX.FILL)
            }
        }
        setTitle("Excluded Versions")
        setCenterPanel(content)
        setPreferredFocusComponent(content)
        addOkAction()
        addCancelAction()
        runInEdt(ModalityState.any()) { // Edit "OK" button text
            okAction.apply {
                setText("Save")
                (this as DialogBuilder.OkActionDescriptor).getAction(dialogWrapper)
            }
        }
        setOkOperation {
            newVersions(model.items.associate { it.packageName to it.versions })
            dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
        }
        setCancelOperation {
            dialogWrapper.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
        dialogWrapper.setSize(400, 300) // Width is not respected, text width takes over
    }

    val panel = panel {
        val settings = NUDSettingsState.instance
        group("Annotation Actions") {
            row("Default update type:") {
                comboBox(Versions.Kind.entries.toList())
                    .bindItem(settings::defaultUpdateType.toMutableProperty())
            }
            row("Default deprecation action:") {
                comboBox(Deprecation.Action.entries.toList().filter { it != Deprecation.Action.IGNORE })
                    .bindItem(settings::defaultDeprecationAction.toMutableProperty())
            }
            row("Default action for \"unmaintained\" packages:") {
                comboBox(Deprecation.Action.entries.toList().filter { it != Deprecation.Action.REPLACE })
                    .bindItem(settings::defaultUnmaintainedAction.toMutableProperty())
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
        group("\"Unmaintained\" Packages") {
            row {
                checkBox("Show a banner for \"unmaintained\" packages")
                    .comment("Show a banner for packages that haven't been updated in a specified amount of time.")
                    .bindSelected(settings::showUnmaintainedBanner)
            }
            row("Days until a package is considered unmaintained:") {
                spinner(0..365 * 10)
                    .comment(
                        """
                        Control how many days a package can go without an update before it's considered "likely unmaintained". Set to 0 to disable.<br>
                        <em>A package might still be maintained even if it hasn't been updated in a while. You can adjust this value to your liking, or even exclude packages from this check if you know they're still maintained.</em>
                        """.trimIndent()
                    )
                    .bindIntValue(settings::unmaintainedDays)
            }
        }
        group("Optimization") {
            row("Maximum parallel processes:") {
                spinner(1..100)
                    .comment(
                        "Control the maximum number of parallel scans that can be run at the same time. Higher values can speed up the scan but might cause performance issues or out of memory issues. Make sure to bump ${
                            ApplicationNamesInfo.getInstance().fullProductName.substringBefore(
                                " "
                            )
                        }'s memory as needed.<br><strong>100 means no limit.</strong>"
                    )
                    .bindIntValue(settings::maxParallelism)
            }
            row("Cache duration (minutes):") {
                spinner(1..60 * 24)
                    .comment("Control how long valid scan results are cached for. Lower values can cause more scans to be run, but higher values can cause outdated results to be shown.")
                    .bindIntValue(settings::cacheDurationMinutes)
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
                    comboBox(StatusBarMode.entries.toList())
                        .comment("Compact mode only shows \"U\" for outdated dependencies and \"D\" for deprecated dependencies.")
                        .bindItem(settings::statusBarMode.toMutableProperty())
                }.enabledIf(statusBarEnabled.selected)
            }
        }
        group("Miscellaneous") {
            row {
                checkBox("Auto-fix on save")
                    .comment("Auto-fix applies the default update type and deprecation action to all dependencies when saving <code>package.json</code>.")
                    .bindSelected(settings::autoFixOnSave)
            }
            row {
                checkBox("Check for updates on static comparators")
                    .comment("Check for updates on static comparators like <code>\"1.2.3\"</code> (without any prefix, or starting with <code>v</code> and/or <code>=</code>).")
                    .bindSelected(settings::checkStaticComparators)
            }
            row {
                checkBox("Suggest replacing tags with versions")
                    .comment("Suggest replacing tags like <code>latest</code> or <code>next</code> with actual versions when a tag is used in a dependency comparator.")
                    .bindSelected(settings::suggestReplacingTags)
            }
        }
        group("Exclusions") {
            row {
                button("Show Excluded Versions") {
                    excludedVersionsPanel(settings.excludedVersions) { newVersions ->
                        if (settings.excludedVersions == newVersions) return@excludedVersionsPanel
                        settings.excludedVersions = newVersions.let {
                            // Remove duplicates and empty lists
                            it.mapValues { (_, versions) ->
                                versions.distinct().filter { version -> version.isNotBlank() }
                            }.filterValues { values ->
                                values.isNotEmpty()
                            }
                        }.toMutableMap()
                    }.showAndGet()
                }
            }
            row("Packages excluded from the \"unmaintained\" check:") {
                textField()
                    .comment("Comma-separated list of package names that should be excluded from the \"unmaintained\" check.")
                    .bindText(settings::excludedUnmaintainedPackages)
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
