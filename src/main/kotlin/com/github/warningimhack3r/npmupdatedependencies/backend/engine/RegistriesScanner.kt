package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class RegistriesScanner {
    companion object {
        private val log = logger<RegistriesScanner>()

        @JvmStatic
        fun getInstance(project: Project): RegistriesScanner = project.service()
    }

    var registries: List<String> = emptyList()

    fun scan() {
        log.info("Starting to scan registries")
        // Run `npm config ls` to get the list of registries
        val config = ShellRunner.execute(arrayOf("npm", "config", "ls")) ?: return
        registries = config.lines().asSequence().filter { line ->
            line.isNotEmpty() && line.isNotBlank() && !line.startsWith(";")
        }.map { it.trim() }.filter { line ->
            line.contains("registry =") || line.contains("registry=")
                    || line.startsWith("//")
        }.map { line ->
            if (line.startsWith("//")) {
                "https:${line.substringBefore("/:")}"
            } else {
                line.substringAfter("registry")
                    .substringAfter("=")
                    .trim()
                    .replace("\"", "")
            }
        }.map { it.removeSuffix("/") }.distinct().toList()
        log.info("Found registries: $registries")
    }
}
