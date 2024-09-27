package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.backend.models.DataState
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.models.Update
import com.github.warningimhack3r.npmupdatedependencies.ui.statusbar.StatusBarHelper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class NUDState {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): NUDState = project.service()
    }

    /**
     * A "cache" of available updates for packages.
     * Managed by [com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers.PackageUpdateChecker.checkAvailableUpdates] and [com.github.warningimhack3r.npmupdatedependencies.ui.annotation.UpdatesAnnotator.doAnnotate].
     */
    val availableUpdates = mutableMapOf<String, DataState<Update>>()

    /**
     * A "cache" of deprecations for packages.
     * Managed by [com.github.warningimhack3r.npmupdatedependencies.backend.engine.checkers.PackageDeprecationChecker.getDeprecationStatus] and [com.github.warningimhack3r.npmupdatedependencies.ui.annotation.DeprecationAnnotator.doAnnotate].
     */
    val deprecations = mutableMapOf<String, DataState<Deprecation>>()

    /**
     * A "cache" of registries for packages, mapping package names to registry URLs.
     * Managed by [NPMJSClient.getRegistry] and only made to be used by it.
     *
     * MUST NOT be accessed from outside the [com.github.warningimhack3r.npmupdatedependencies.backend.engine] package.
     */
    val packageRegistries = mutableMapOf<String, String>()

    var totalPackages = 0
        set(value) {
            field = value
            StatusBarHelper.updateWidget()
        }
    var isScanningForUpdates = false
        set(value) {
            field = value
            StatusBarHelper.updateWidget()
        }
    var scannedUpdates = 0
        set(value) {
            field = value
            StatusBarHelper.updateWidget()
        }
    var isScanningForDeprecations = false
        set(value) {
            field = value
            StatusBarHelper.updateWidget()
        }
    var scannedDeprecations = 0
        set(value) {
            field = value
            StatusBarHelper.updateWidget()
        }
    var isScanningForRegistries = false
        set(value) {
            field = value
            StatusBarHelper.updateWidget()
        }
}
