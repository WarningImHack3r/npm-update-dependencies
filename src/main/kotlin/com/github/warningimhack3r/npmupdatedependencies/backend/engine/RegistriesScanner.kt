package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

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
        // Run `npm config ls` to get the list of registries
        val config = ShellRunner.getInstance(project).execute(arrayOf("npm", "config", "ls")) ?: return
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
