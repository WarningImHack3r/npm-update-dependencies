package com.github.warningimhack3r.npmupdatedependencies.ui.statusbar

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.availableUpdates
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.deprecations
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.isScanningForDeprecations
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.isScanningForUpdates
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.intellij.dvcs.ui.LightActionGroup
import com.intellij.ide.DataManager
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
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

class StatusBarFactory : StatusBarEditorBasedWidgetFactory() {
    companion object {
        const val ID = "NpmUpdateDependenciesStatusBarEditorFactory"
    }
    override fun getId(): String = ID

    override fun getDisplayName(): String = "NPM Update Dependencies Status Bar"

    override fun createWidget(project: Project): StatusBarWidget = WidgetBar(project)

    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
}

class WidgetBar(project: Project) : EditorBasedWidget(project), StatusBarWidget.MultipleTextValuesPresentation {
    companion object {
        const val ID = "NpmUpdateDependenciesStatusBarWidgetBar"
    }
    private var currentStatus = Status.UNAVAILABLE

    enum class Status {
        UNAVAILABLE,
        SCANNING,
        READY
    }

    // EditorBasedWidget
    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    // MultipleTextValuesPresentation
    override fun getTooltipText(): String = when (currentStatus) {
        Status.UNAVAILABLE -> "NPM Update Dependencies is not available"
        Status.SCANNING -> "Scanning for updates..."
        Status.READY -> "Click to see available updates"
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? = null

    // Replaced with getPopup() in 2023.1
    override fun getPopupStep(): ListPopup? {
        if (project.isDisposed || currentStatus != Status.READY) return null

        fun openPackageJson(dependencyName: String) {
            FilenameIndex.getVirtualFilesByName("package.json", GlobalSearchScope.allScope(project)).filter {
                 !it.path.contains("node_modules")
            }.run {
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
                // Find the column number of at the end of the dependency line
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
            LightActionGroup().apply {
                addSeparator("Updates")
                addAll(availableUpdates.toSortedMap().map { update ->
                    DumbAwareAction.create(update.key) {
                        openPackageJson(update.key)
                    }
                })
                addSeparator("Deprecations")
                addAll(deprecations.toSortedMap().map { deprecation ->
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

    @SuppressWarnings("kotlin:S3776") // nested yes, but not complex
    override fun getSelectedValue(): String? {
        if (!NUDSettingsState.instance.showStatusBarWidget) return null
        return when (currentStatus) {
            Status.UNAVAILABLE -> null
            Status.SCANNING -> "Scanning dependencies..."
            Status.READY -> {
                val outdated = availableUpdates.size
                val deprecated = deprecations.size
                when (NUDSettingsState.instance.statusBarMode) {
                    // Full
                    0 -> when {
                        outdated == 0 && deprecated == 0 -> null
                        outdated == 0 -> "$deprecated deprecation${if (deprecated == 1) "" else "s"}"
                        deprecated == 0 -> "$outdated update${if (outdated == 1) "" else "s"}"
                        else -> "$outdated update${if (outdated == 1) "" else "s"}, $deprecated deprecation${if (deprecated == 1) "" else "s"}"
                    }
                    // Compact
                    1 -> when {
                        outdated == 0 && deprecated == 0 -> null
                        outdated == 0 -> "$deprecated D"
                        deprecated == 0 -> "$outdated U"
                        else -> "$outdated U / $deprecated D"
                    }
                    else -> null
                }
            }
        }
    }

    // Custom
    fun update() {
        currentStatus = when {
            project.isDisposed || !NUDSettingsState.instance.showStatusBarWidget -> Status.UNAVAILABLE
            isScanningForUpdates || isScanningForDeprecations -> Status.SCANNING
            else -> Status.READY
        }
        myStatusBar.updateWidget(ID())
    }
}
