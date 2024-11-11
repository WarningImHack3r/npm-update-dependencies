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

        @JvmStatic
        fun getInstance(project: Project): RegistriesScanner = project.service()
    }

    /**
     * Whether the registries have been scanned for this project.
     */
    var scanned = false

    /**
     * The list of registries found parsing the npm configuration.
     */
    var registries: List<String> = emptyList()

    fun scan() {
        log.info("Starting to scan registries")
        val shellRunner = ShellRunner.getInstance(project)
        val state = NUDState.getInstance(project)
        // Populate packageRegistries with the contents of `npm ls --json`
        shellRunner.execute(arrayOf("npm", "ls", "--json"))?.let { output ->
            val json = try {
                Json.parseToJsonElement(output)
            } catch (e: SerializationException) {
                log.warn("Failed to parse JSON from npm ls --json", e)
                return@let
            }
            val jsonElement = json.asJsonObject ?: return@let.also {
                log.warn("Failed to extract object from npm ls --json")
            }
            val dependencies = jsonElement["dependencies"]?.asJsonObject ?: return@let.also {
                log.warn("No dependencies found in JSON from npm ls --json")
            }
            val registriesSet = mutableSetOf<String>()
            for (packageName in dependencies.keys) {
                val values = dependencies[packageName]?.asJsonObject ?: continue.also {
                    log.warn("No values found for package $packageName in JSON from npm ls --json")
                }
                if (values.keys.contains("extraneous")) {
                    log.debug("Package $packageName is extraneous, skipping")
                    continue
                }
                val registry = dependencies[packageName]?.asJsonObject?.get("resolved")?.asString ?: continue.also {
                    log.warn("No resolved found for package $packageName in JSON from npm ls --json")
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
            registries = registriesSet.toList()
            log.debug("Found ${registries.size} registries and ${state.packageRegistries.size} package registries bindings from `npm ls --json`")
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
        // Run `npm config ls` to get the list of registries
        val config = shellRunner.execute(arrayOf("npm", "config", "ls")) ?: return
        registries = config.lines().asSequence().map { it.trim() }.filter { line ->
            line.isNotEmpty() && line.isNotBlank() && !line.startsWith(";") &&
                    (line.contains("registry =") || line.contains("registry=")
                            || line.startsWith("//"))
        }.map { line ->
            if (line.startsWith("//")) {
                // We assume that registries use TLS in 2024
                "https:${line.substringBefore("/:")}"
            } else {
                line.substringAfter("registry")
                    .substringAfter("=")
                    .trim()
                    .replace("\"", "")
            }
        }.map { it.removeSuffix("/") }.distinct().toList()
        log.info("Found registries: $registries")
        if (!scanned) scanned = true
    }
}
