package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jetbrains.rd.util.printlnError
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object NPMJSClient {
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
            JsonParser.parseString(responseBody).asJsonObject
        } catch (e: Exception) {
            printlnError("Error while parsing response body from $uri: ${e.message}")
            null
        }
    }

    fun getLatestVersion(packageName: String): String? {
        val json = getBodyAsJSON("https://registry.npmjs.org/$packageName/latest")
        return json?.get("version")?.asString
    }

    fun getAllVersions(packageName: String): List<String>? {
        val json = getBodyAsJSON("https://registry.npmjs.org/$packageName")
        return json?.get("versions")?.asJsonObject?.keySet()?.toList()
    }

    fun getPackageDeprecation(packageName: String): String? {
        val json = getBodyAsJSON("https://registry.npmjs.org/$packageName/latest")
        return json?.get("deprecated")?.asString
    }
}
