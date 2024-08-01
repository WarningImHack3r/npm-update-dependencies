package com.github.warningimhack3r.npmupdatedependencies.ui.statusbar

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

class StatusBarFactory : StatusBarEditorBasedWidgetFactory() {
    companion object {
        const val ID = "NpmUpdateDependenciesStatusBarEditorFactory"
    }

    override fun getId(): String = ID

    override fun getDisplayName(): String = "NPM Update Dependencies Status Bar"

    override fun createWidget(project: Project): StatusBarWidget = WidgetBar(project)

    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
}

enum class StatusBarMode {
    FULL, COMPACT;

    override fun toString(): String {
        return when (this) {
            FULL -> "Full"
            COMPACT -> "Compact"
        }
    }
}

class WidgetBar(project: Project) : EditorBasedWidget(project), StatusBarWidget.MultipleTextValuesPresentation {
    companion object {
        const val ID = "NpmUpdateDependenciesStatusBarWidgetBar"
    }

    private var currentStatus = Status.UNAVAILABLE

    enum class Status {
        UNAVAILABLE,
        GATHERING_REGISTRIES,
        SCANNING_PACKAGES,
        SCANNING_FOR_UPDATES,
        SCANNING_FOR_DEPRECATIONS,
        READY
    }

    // EditorBasedWidget
    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    // MultipleTextValuesPresentation
    override fun getTooltipText(): String = when (currentStatus) {
        Status.UNAVAILABLE -> "NPM Update Dependencies is not available"
        Status.GATHERING_REGISTRIES -> "Gathering package registries..."
        Status.SCANNING_PACKAGES -> "Scanning packages..."
        Status.SCANNING_FOR_UPDATES -> "Scanning for updates..."
        Status.SCANNING_FOR_DEPRECATIONS -> "Scanning for deprecations..."
        Status.READY -> "Click to see available updates"
    }

    @Deprecated("Replaced with getPopup() in 2023.1", ReplaceWith("getPopup()"))
    override fun getPopupStep(): ListPopup? {
        if (project.isDisposed || currentStatus != Status.READY) return null

        fun openPackageJson(dependencyName: String) {
            FilenameIndex.getVirtualFilesByName(
                "package.json",
                GlobalSearchScope.projectScope(project)
            ).filter { !it.path.contains("node_modules") }.run {
                if (size == 1) first() else {
                    // Filter all files containing the dependency name
                    filter { file ->
                        FileDocumentManager.getInstance().getDocument(file)?.text?.contains(dependencyName) ?: false
                    }.run {
                        if (size == 1) first() else {
                            // Show a dialog to choose the file
                            StatusBarItemChooserDialog(map {
                                it.path.substring(project.basePath?.length?.plus(1) ?: 0)
                            }).showAndGet()?.let {
                                getOrNull(it)
                            }
                        }
                    }
                }
            }?.let { file ->
                val document = FileDocumentManager.getInstance().getDocument(file)
                // Find the line number of the dependency
                val lineNumber = document?.let {
                    it.text.split("\n").indexOfFirst { line ->
                        line.contains("\"$dependencyName\":")
                    }
                } ?: 0
                // Find the column number at the end of the dependency line
                val columnNumber = document?.let {
                    it.text.split("\n").getOrNull(lineNumber)?.replace("\t", "    ")?.indexOfFirst { char ->
                        char == ','
                    }?.plus(1)
                } ?: 0
                // Open the file
                OpenFileDescriptor(project, file, lineNumber, columnNumber).navigate(true)
            }
        }

        return JBPopupFactory.getInstance().createActionGroupPopup(
            "Available Changes",
            DefaultActionGroup().apply {
                addSeparator("Updates")
                addAll(NUDState.getInstance(project).availableUpdates.toSortedMap().map { update ->
                    DumbAwareAction.create(update.key) {
                        openPackageJson(update.key)
                    }
                })
                addSeparator("Deprecations")
                addAll(NUDState.getInstance(project).deprecations.toSortedMap().map { deprecation ->
                    DumbAwareAction.create(deprecation.key) {
                        openPackageJson(deprecation.key)
                    }
                })
            },
            DataManager.getInstance().getDataContext(myStatusBar.component),
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false, null, 5
        )
    }

    override fun getSelectedValue(): String? {
        if (!NUDSettingsState.instance.showStatusBarWidget) return null
        val state = NUDState.getInstance(project)
        return when (currentStatus) {
            Status.UNAVAILABLE -> null
            Status.GATHERING_REGISTRIES -> "Gathering registries..."
            Status.SCANNING_PACKAGES -> "Scanning packages (${state.scannedUpdates + state.scannedDeprecations}/${state.totalPackages * 2})..."
            Status.SCANNING_FOR_UPDATES -> "Scanning for updates (${state.scannedUpdates}/${state.totalPackages})..."
            Status.SCANNING_FOR_DEPRECATIONS -> "Scanning for deprecations (${state.scannedDeprecations}/${state.totalPackages})..."
            Status.READY -> {
                val outdated = state.availableUpdates.size
                val deprecated = state.deprecations.size
                when (NUDSettingsState.instance.statusBarMode) {
                    StatusBarMode.FULL -> when {
                        outdated == 0 && deprecated == 0 -> null
                        outdated == 0 -> "$deprecated deprecation${if (deprecated == 1) "" else "s"}"
                        deprecated == 0 -> "$outdated update${if (outdated == 1) "" else "s"}"
                        else -> "$outdated update${if (outdated == 1) "" else "s"}, $deprecated deprecation${if (deprecated == 1) "" else "s"}"
                    }

                    StatusBarMode.COMPACT -> when {
                        outdated == 0 && deprecated == 0 -> null
                        outdated == 0 -> "$deprecated D"
                        deprecated == 0 -> "$outdated U"
                        else -> "$outdated U / $deprecated D"
                    }

                    null -> null
                }
            }
        }
    }

    // Custom
    fun update() {
        val state = NUDState.getInstance(project)
        currentStatus = when {
            project.isDisposed || !NUDSettingsState.instance.showStatusBarWidget -> Status.UNAVAILABLE
            state.isScanningForRegistries -> Status.GATHERING_REGISTRIES
            state.isScanningForUpdates && state.isScanningForDeprecations -> Status.SCANNING_PACKAGES
            state.isScanningForUpdates -> Status.SCANNING_FOR_UPDATES
            state.isScanningForDeprecations -> Status.SCANNING_FOR_DEPRECATIONS
            else -> Status.READY
        }
        myStatusBar.updateWidget(ID())
    }
}
