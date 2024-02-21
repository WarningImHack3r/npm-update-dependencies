package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.parallelMap
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.printlnError
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Service(Service.Level.PROJECT)
class NPMJSClient(private val project: Project) {
    companion object {
        private const val NPMJS_REGISTRY = "https://registry.npmjs.org"
    }

    private fun getRegistry(packageName: String): String {
        val registryForPackage = project.service<NUDState>().packageRegistries
        val availableRegistries = project.service<RegistriesScanner>().registries
        return registryForPackage[packageName] ?: ShellRunner.execute(
            arrayOf("npm", "v", packageName, "dist.tarball")
        )?.trim()?.let { dist ->
            val computedRegistry = dist.ifEmpty {
                availableRegistries.parallelMap { registry ->
                    ShellRunner.execute(
                        arrayOf("npm", "v", packageName, "dist.tarball", "--registry=$registry")
                    )?.trim()?.let { regDist ->
                        regDist.ifEmpty { null }
                    }
                }.firstNotNullOfOrNull { it } ?: return@let null
            }
            val registry = "${computedRegistry.substringBefore("/$packageName")}/"
            registryForPackage[packageName] = registry
            registry
        } ?: NPMJS_REGISTRY
    }

    private fun getResponseBody(uri: URI): String {
        val request = HttpRequest
            .newBuilder(uri)
            .build()
        return HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString())
            .body()
    }

    private fun getBodyAsJSON(uri: String): JsonObject? {
        val responseBody: String
        try {
            responseBody = getResponseBody(URI(uri))
        } catch (e: Exception) {
            printlnError("Error while getting response body from $uri: ${e.message}")
            return null
        }
        return try {
            if (JsonParser.parseString(responseBody).isJsonObject) {
                JsonParser.parseString(responseBody).asJsonObject
            } else {
                null
            }
        } catch (e: Exception) {
            printlnError("Error while parsing response body from $uri: ${e.message}")
            null
        }
    }

    fun getLatestVersion(packageName: String): String? {
        val registry = getRegistry(packageName)
        val json = getBodyAsJSON("${registry}/$packageName/latest")
        return json?.get("version")?.asString ?: ShellRunner.execute(
            arrayOf("npm", "v", packageName, "version", "--registry=$registry")
        )?.trim()?.let { it.ifEmpty { null } }
    }

    fun getAllVersions(packageName: String): List<String>? {
        val registry = getRegistry(packageName)
        val json = getBodyAsJSON("${registry}/$packageName")
        return json?.get("versions")?.asJsonObject?.keySet()?.toList() ?: ShellRunner.execute(
            arrayOf("npm", "v", packageName, "versions", "--json", "--registry=$registry")
        )?.trim()?.let { versions ->
            if (versions.isEmpty()) {
                return null
            } else if (versions.startsWith("[")) {
                JsonParser.parseString(versions).asJsonArray.map { it.asString }
            } else {
                listOf(versions.replace("\"", ""))
            }
        }
    }

    fun getPackageDeprecation(packageName: String): String? {
        val registry = getRegistry(packageName)
        val json = getBodyAsJSON("${registry}/$packageName/latest")
        return json?.get("deprecated")?.asString ?: ShellRunner.execute(
            arrayOf("npm", "v", packageName, "deprecated", "--registry=$registry")
        )?.trim()?.let { it.ifEmpty { null } }
    }
}
