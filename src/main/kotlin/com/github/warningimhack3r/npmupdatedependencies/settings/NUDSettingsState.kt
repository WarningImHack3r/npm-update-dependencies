package com.github.warningimhack3r.npmupdatedependencies.settings

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.github.warningimhack3r.npmupdatedependencies.ui.statusbar.StatusBarMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil.copyBean

@State(name = "NUDSettings", storages = [Storage("npm-update-dependencies.xml")])
class NUDSettingsState : PersistentStateComponent<NUDSettingsState.Settings> {
    companion object {
        val instance: NUDSettingsState
            get() = ApplicationManager.getApplication().getService(NUDSettingsState::class.java)
    }

    private var settings = Settings()

    override fun getState(): Settings = settings

    override fun loadState(state: Settings) {
        copyBean(state, settings)
    }

    // Settings
    var defaultUpdateType: Versions.Kind?
        get() = settings.defaultUpdateType
        set(value) {
            if (value != null) settings.defaultUpdateType = value
        }
    var defaultDeprecationAction: Deprecation.Action?
        get() = settings.defaultDeprecationAction
        set(value) {
            if (value != null) settings.defaultDeprecationAction = value
        }
    var showDeprecationBanner: Boolean
        get() = settings.showDeprecationBanner
        set(value) {
            settings.showDeprecationBanner = value
        }
    var autoReorderDependencies: Boolean
        get() = settings.autoReorderDependencies
        set(value) {
            settings.autoReorderDependencies = value
        }
    var maxParallelism: Int
        get() = settings.maxParallelism
        set(value) {
            settings.maxParallelism = value
        }
    var showStatusBarWidget: Boolean
        get() = settings.showStatusBarWidget
        set(value) {
            settings.showStatusBarWidget = value
        }
    var statusBarMode: StatusBarMode?
        get() = settings.statusBarMode
        set(value) {
            if (value != null) settings.statusBarMode = value
        }
    var autoFixOnSave: Boolean
        get() = settings.autoFixOnSave
        set(value) {
            settings.autoFixOnSave = value
        }
    var excludedVersions: MutableMap<String, List<String>>
        get() = settings.excludedVersions
        set(value) {
            settings.excludedVersions = value
        }

    data class Settings(
        var defaultUpdateType: Versions.Kind = Versions.Kind.SATISFIES,
        var defaultDeprecationAction: Deprecation.Action = Deprecation.Action.REPLACE,
        var showDeprecationBanner: Boolean = true,
        var autoReorderDependencies: Boolean = true,
        var maxParallelism: Int = 100,
        var showStatusBarWidget: Boolean = true,
        var statusBarMode: StatusBarMode = StatusBarMode.FULL,
        var autoFixOnSave: Boolean = false,
        var excludedVersions: MutableMap<String, List<String>> = emptyMap<String, List<String>>().toMutableMap()
    )
}
