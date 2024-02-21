package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.intellij.openapi.components.Service

@Service(Service.Level.PROJECT)
class RegistriesScanner {
    var registries: List<String> = emptyList()

    fun scan() {
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
    }
}
