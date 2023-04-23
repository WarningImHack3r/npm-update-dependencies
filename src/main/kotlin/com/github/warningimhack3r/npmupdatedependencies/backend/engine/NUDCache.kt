package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.data.Versions

object NUDCache {
    val availableUpdates = mutableMapOf<String, Versions>()
    val deprecations = mutableMapOf<String, Deprecation>()

    var isScanningForUpdates = false
    var isScanningForDeprecations = false
}
