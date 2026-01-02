package com.github.warningimhack3r.npmupdatedependencies

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId


object NUDConstants {
    const val PLUGIN_ID = "com.github.warningimhack3r.npmupdatedependencies"
    const val NPMJS_REGISTRY = "https://registry.npmjs.com"
    const val PACKAGE_JSON = "package.json"
    val dependenciesKeys = listOf("dependencies", "devDependencies")
    const val PACKAGE_MANAGER_KEY = "packageManager"

    private val log = logger<NUDConstants>()

    init {
        log.info(
            "Initializing NPM Update Dependencies plugin v${
                PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "???"
            }"
        )
    }
}
