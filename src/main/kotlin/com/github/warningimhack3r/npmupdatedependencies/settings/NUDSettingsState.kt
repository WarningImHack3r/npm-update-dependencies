package com.github.warningimhack3r.npmupdatedependencies.settings

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions
import com.github.warningimhack3r.npmupdatedependencies.ui.statusbar.StatusBarHelper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.XmlSerializerUtil.copyBean
import java.lang.reflect.Modifier

@State(name = "NUDSettings", storages = [(Storage("npm-update-dependencies.xml"))])
class NUDSettingsState : PersistentStateComponent<NUDSettingsState> {
    companion object {
        val instance: NUDSettingsState
            get() = ApplicationManager.getApplication().getService(NUDSettingsState::class.java)
    }

    // Settings
    var defaultUpdateType: Int = Versions.Kind.SATISFIES.ordinal
    var defaultDeprecationAction: Int = Deprecation.Action.REPLACE.ordinal
    var showDeprecationBanner: Boolean = true
    var autoReorderDependencies: Boolean = true
    var showStatusBarWidget: Boolean = true
    var statusBarMode: Int = 0
    var autoFixOnSave: Boolean = false

    // Map of the settings
    @get:Transient
    var mapped = mapOf<String, Any>()
        get() = javaClass.declaredFields.filter {
                !Modifier.isStatic(it.modifiers) && it.type != Map::class.java
            }.associate { f ->
                f.name to f.get(this)
            }
        set(values) {
            field = values
            values.forEach { (key, value) ->
                javaClass.declaredFields.first { field ->
                    field.name == key
                }.set(this, value).also {
                    if (key.lowercase().contains("statusbar")) {
                        StatusBarHelper.updateWidget()
                    }
                }
            }
        }

    override fun getState(): NUDSettingsState = this

    override fun loadState(state: NUDSettingsState) {
        copyBean(state, this)
    }
}
