package com.github.warningimhack3r.npmupdatedependencies.backend

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object NPMJSClient {
    private val client = HttpClient.newHttpClient()

    private fun getResponseBody(uri: URI): String {
        val request = HttpRequest
            .newBuilder(uri)
            .build()
        return client
            .send(request, HttpResponse.BodyHandlers.ofString())
            .body()
    }

    fun getLatestVersion(packageName: String): String {
        val responseBody = getResponseBody(URI("https://registry.npmjs.org/$packageName/latest"))
        // Parse response body as JSON
        val json = JsonParser.parseString(responseBody).asJsonObject
        return json["version"]!!.asString
    }

    fun getAllVersions(packageName: String): List<String> {
        val responseBody = getResponseBody(URI("https://registry.npmjs.org/$packageName"))
        // Parse response body as JSON
        val json = JsonParser.parseString(responseBody).asJsonObject
        return json["versions"]!!.asJsonObject!!.keySet()!!.toList()
    }
}
