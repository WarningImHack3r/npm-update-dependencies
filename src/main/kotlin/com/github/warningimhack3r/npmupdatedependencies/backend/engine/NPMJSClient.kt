package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDCache.packageRegistries
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jetbrains.rd.util.printlnError
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object NPMJSClient {
    private const val NPMJS_REGISTRY = "https://registry.npmjs.org"

    private fun getRegistry(packageName: String): String {
        return packageRegistries[packageName] ?: ShellRunner.execute(
            arrayOf("npm", "v", packageName, "dist.tarball")
        )?.trim()?.let { dist ->
            if (dist.isEmpty()) return@let null
            try {
                URI(dist).let { uri ->
                    val registry = "${uri.scheme}://${uri.host}"
                    packageRegistries[packageName] = registry
                    registry
                }
            } catch (e: Exception) {
                printlnError("Error while getting registry from \"$dist\": ${e.message}")
                NPMJS_REGISTRY
            }
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
        val json = getBodyAsJSON("${getRegistry(packageName)}/$packageName/latest")
        return json?.get("version")?.asString ?: ShellRunner.execute(
            arrayOf("npm", "v", packageName, "version")
        )?.trim()?.let { it.ifEmpty { null } }
    }

    fun getAllVersions(packageName: String): List<String>? {
        val json = getBodyAsJSON("${getRegistry(packageName)}/$packageName")
        return json?.get("versions")?.asJsonObject?.keySet()?.toList() ?: ShellRunner.execute(
            arrayOf("npm", "v", packageName, "versions", "--json")
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
        val json = getBodyAsJSON("${getRegistry(packageName)}/$packageName/latest")
        return json?.get("deprecated")?.asString ?: ShellRunner.execute(
            arrayOf("npm", "v", packageName, "deprecated")
        )?.trim()?.let { it.ifEmpty { null } }
    }
}
