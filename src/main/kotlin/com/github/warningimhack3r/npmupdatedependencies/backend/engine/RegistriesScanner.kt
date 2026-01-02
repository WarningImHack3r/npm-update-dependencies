package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asJsonObject
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asString
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Service(Service.Level.PROJECT)
class RegistriesScanner(private val project: Project) {
    companion object {
        private val log = logger<RegistriesScanner>()
    }

    /**
     * Whether the registries have been scanned for this project.
     */
    var scanned = false

    /**
     * The list of registries found parsing the npm configuration.
     */
    var registries = setOf<String>()

    fun scan() {
        log.info("Starting to scan registries")
        val shellRunner = project.service<ShellRunner>()
        val state = project.service<NUDState>()
        state.packageRegistries.clear()
        // Populate packageRegistries with the contents of `npm ls --json`
        shellRunner.execute(arrayOf("npm", "ls", "--json"))?.let { output ->
            val json = try {
                Json.parseToJsonElement(output)
            } catch (e: SerializationException) {
                log.warn("Failed to parse JSON from npm ls --json", e)
                return@let
            }
            val jsonElement = json.asJsonObject ?: run {
                log.warn("Failed to extract object from npm ls --json")
                return@let
            }
            val dependencies = jsonElement["dependencies"]?.asJsonObject ?: run {
                log.warn("No dependencies found in JSON from npm ls --json")
                return@let
            }
            val registriesSet = mutableSetOf<String>()
            for (packageName in dependencies.keys) {
                val values = dependencies[packageName]?.asJsonObject ?: run {
                    log.warn("No values found for package $packageName in JSON from npm ls --json")
                    continue
                }
                if (values.keys.contains("extraneous")) {
                    log.debug("Package $packageName is extraneous, skipping")
                    continue
                }
                val registry = dependencies[packageName]?.asJsonObject?.get("resolved")?.asString ?: run {
                    log.warn("No resolved found for package $packageName in JSON from npm ls --json")
                    continue
                }
                if (!registry.startsWith("http")) {
                    log.warn("Invalid registry $registry for package $packageName, skipping")
                    continue
                }
                val formattedRegistry = registry.substringBefore("/$packageName")
                log.debug("Found registry $formattedRegistry for package $packageName")
                registriesSet.add(formattedRegistry)
                state.packageRegistries[packageName] = formattedRegistry
            }
            registries = registriesSet
            log.debug("Found ${state.packageRegistries.size} package registries with bindings from `npm ls --json`")
        }
        if (registries.isNotEmpty()) {
            // There are extra thin chances of missing registries with
            // this logic, like when a package with a custom registry is
            // installed with pnpm BUT at least one other is installed
            // with npm.
            // We'll consider here such edge cases negligible (and the user's
            // responsibility), prioritizing speed and simplicity instead of
            // slowing things down for everyone because of a few weird cases.
            log.info("Found registries from `npm ls`: $registries")
            if (!scanned) scanned = true
            return
        }
        // Get the list of registries
        val registriesItems = project.service<NPMConfigReader>().getAllRegistries()
        registries = registriesItems.map { it.url }.toSet()
        registriesItems.filterIsInstance<NPMConfigReader.Registry.ScopedRegistry>().forEach { item ->
            state.packageRegistries[item.scope] = item.url
        }
        log.info("Found registries: $registries")
        if (!scanned) scanned = true
    }
}
