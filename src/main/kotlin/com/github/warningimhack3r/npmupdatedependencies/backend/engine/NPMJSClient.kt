package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.warningimhack3r.npmupdatedependencies.NUDConstants.NPMJS_REGISTRY
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asBoolean
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asJsonObject
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asString
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.filterNotNullValues
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class NPMJSClient(private val project: Project) {
    companion object {
        private val log = logger<NPMJSClient>()
        private val httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        @JvmStatic
        fun getInstance(project: Project): NPMJSClient = project.service()
    }

    val cache: Cache<String, String> = Caffeine.newBuilder()
        .expireAfterWrite(NUDSettingsState.instance.cacheDurationMinutes.toLong(), TimeUnit.MINUTES)
        .removalListener<String, String> { k, _, removalCause ->
            log.debug("Package $k removed from cache: $removalCause")
        }
        .build()

    private fun getRegistry(packageName: String): String {
        log.info("Getting registry for package $packageName")
        val state = NUDState.getInstance(project)
        return state.packageRegistries[packageName]?.also {
            log.debug("Registry for package $packageName found in cache: $it")
        } ?: setOf(
            NPMJS_REGISTRY,
            *RegistriesScanner.getInstance(project).registries.toTypedArray()
        ).firstOrNull { registry ->
            log.debug("Trying registry $registry for package $packageName")
            getResponseStatus(URI("$registry/$packageName")) == HttpStatusCode.OK.value
        }?.let { computedRegistry ->
            log.debug("Found registry for $packageName: $computedRegistry")
            state.packageRegistries[packageName] = computedRegistry
            computedRegistry
        } ?: NPMJS_REGISTRY.also {
            log.warn("No registry found for $packageName, using default")
        }
    }

    private fun getResponseStatus(uri: URI): Int {
        log.debug("HEAD $uri")
        val request = HttpRequest.newBuilder(uri)
            .method(HttpMethod.Head.value, HttpRequest.BodyPublishers.noBody())
            .build()
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            return response.statusCode()
        } catch (_: Exception) {
            log.warn("Failed to fetch $uri, returning Internal Server Error")
            return HttpStatusCode.InternalServerError.value
        }
    }

    private fun getResponseBody(uri: URI): String {
        cache.getIfPresent(uri.toString())?.let { cachedData ->
            log.debug("GET $uri (from cache)")
            return cachedData
        }

        log.debug("GET $uri")
        val request = HttpRequest
            .newBuilder(uri)
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != HttpStatusCode.OK.value) {
            throw Exception("Non-ok status code from $uri: ${response.statusCode()}")
        }
        return response.body().also { body ->
            log.debug("Caching response for $uri")
            cache.put(uri.toString(), body)
        }
    }

    private fun getBodyAsJSON(uri: String): JsonObject? {
        val responseBody: String
        try {
            responseBody = getResponseBody(URI(uri))
        } catch (e: Exception) {
            log.warn("Error while getting response body from $uri", e)
            return null
        }
        return Json.parseToJsonElement(responseBody).asJsonObject
    }

    fun getVersionFromTag(packageName: String, tag: String): String? {
        log.info("Getting version for package $packageName with tag $tag")
        val registry = getRegistry(packageName)
        return getBodyAsJSON("$registry/$packageName/$tag")?.get("version")?.asString?.also {
            log.info("Version for package $packageName with tag $tag found online: $it")
        }.also {
            if (it == null) {
                log.warn("Version for package $packageName with tag $tag not found")
            }
        }
    }

    fun getAllTags(packageName: String): Map<String, String>? {
        log.info("Getting all tags for package $packageName")
        val registry = getRegistry(packageName)
        return getBodyAsJSON("$registry/$packageName")?.get("dist-tags")?.asJsonObject?.toMap()
            ?.mapValues { it.value.toString().replace("\"", "") }?.filterNotNullValues()?.also {
                log.info("All tags for package $packageName found in online (${it.size} tags)")
                log.debug("Tags for $packageName: $it")
            }.also {
                if (it == null) {
                    log.warn("All tags for package $packageName not found")
                }
            }
    }

    fun getAllVersions(packageName: String): List<String>? {
        log.info("Getting all versions for package $packageName")
        val registry = getRegistry(packageName)
        return getBodyAsJSON("$registry/$packageName")?.get("versions")?.asJsonObject?.keys?.toList()?.also {
            log.info("All versions for package $packageName found in online (${it.size} versions)")
            log.debug("Versions for $packageName: $it")
        }.also {
            if (it == null) {
                log.warn("All versions for package $packageName not found")
            }
        }
    }

    private fun deprecationStatus(packageName: String, deprecation: String, local: Boolean = false): String? {
        log.debug("Deprecation status for package $packageName before transformation: $deprecation")
        return with(deprecation) {
            when {
                local && isBlank() -> null
                equals("true", ignoreCase = true) || (!local && isBlank()) -> "Deprecated"
                equals("false", ignoreCase = true) -> null
                else -> this
            }
        }.also { reason ->
            log.debug("Deprecation status for package $packageName after transformation: $reason")
        }
    }

    fun getPackageDeprecation(packageName: String, currentVersion: String = "latest"): String? {
        log.info("Getting deprecation status for package $packageName with version $currentVersion")
        val registry = getRegistry(packageName)
        return getBodyAsJSON("$registry/$packageName/$currentVersion")?.let { json ->
            val deprecation = json["deprecated"] ?: run {
                log.debug("No deprecation present in online response for package $packageName")
                return null
            }
            log.debug("Deprecation status for package $packageName found online: $deprecation")
            when (deprecation.asBoolean) {
                true -> "Deprecated"
                false -> return null
                null -> deprecation.asString?.let {
                    deprecationStatus(packageName, it)
                }.let { reason ->
                    if (reason == null) return null
                    reason
                }
            }
        }.also {
            if (it == null) {
                log.debug("No deprecation status found for package $packageName")
            }
        }
    }

    fun getPackageLastModified(packageName: String): String? {
        log.info("Getting last modified date for package $packageName")
        val registry = getRegistry(packageName)
        return getBodyAsJSON("$registry/$packageName")?.get("time")?.asJsonObject?.get("modified")?.asString?.also {
            log.info("Last modified date for package $packageName found online: $it")
        }.also {
            if (it == null) {
                log.warn("Last modified date for package $packageName not found")
            }
        }
    }
}
