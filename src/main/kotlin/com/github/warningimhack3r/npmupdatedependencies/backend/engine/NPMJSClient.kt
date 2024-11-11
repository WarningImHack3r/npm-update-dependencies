package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asBoolean
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asJsonArray
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asJsonObject
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asString
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.filterNotNullValues
import com.github.warningimhack3r.npmupdatedependencies.settings.NUDSettingsState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.ktor.http.HttpStatusCode
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
        private const val NPMJS_REGISTRY = "https://registry.npmjs.com"
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
        val shellRunner = ShellRunner.getInstance(project)
        return state.packageRegistries[packageName]?.also {
            log.debug("Registry for package $packageName found in cache: $it")
        } ?: shellRunner.execute(
            arrayOf("npm", "v", packageName, "dist.tarball")
        )?.trim()?.let { dist ->
            val computedRegistry = dist.ifEmpty {
                log.debug("No dist.tarball found for package $packageName, trying all registries")
                RegistriesScanner.getInstance(project).registries.forEach { registry ->
                    shellRunner.execute(
                        arrayOf("npm", "v", packageName, "dist.tarball", "--registry=$registry")
                    )?.let { regDist ->
                        log.debug("Found dist.tarball for package $packageName in registry $regDist")
                        return@ifEmpty regDist
                    }
                }
                return@let null.also {
                    log.debug("No dist.tarball found for package $packageName in any registry")
                }
            }.substringBefore("/$packageName").ifEmpty {
                log.debug("No registry found for package $packageName")
                return@let null
            }
            log.info("Computed registry for package $packageName: $computedRegistry")
            state.packageRegistries[packageName] = computedRegistry
            computedRegistry
        } ?: NPMJS_REGISTRY.also {
            log.info("Using default registry for package $packageName")
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
        } ?: ShellRunner.getInstance(project).execute(
            arrayOf("npm", "v", packageName, "dist-tags.$tag", "--registry=$registry")
        )?.trim()?.let { it.ifEmpty { null } }.also {
            if (it != null) {
                log.info("Version for package $packageName with tag $tag found locally: $it")
            } else {
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
            } ?: ShellRunner.getInstance(project).execute(
            arrayOf("npm", "v", packageName, "dist-tags", "--json", "--registry=$registry")
        )?.trim()?.let { it.ifEmpty { null } }?.let { tags ->
            try {
                Json.parseToJsonElement(tags)
            } catch (e: Exception) {
                log.warn("Error while parsing all tags for package $packageName", e)
                null
            }?.asJsonObject?.toMap()?.mapValues { it.value.toString().replace("\"", "") }?.filterNotNullValues()?.also {
                log.info("All tags for package $packageName found locally (${it.size} tags)")
                log.debug("Local tags for $packageName: $it")
            }
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
        } ?: ShellRunner.getInstance(project).execute(
            arrayOf("npm", "v", packageName, "versions", "--json", "--registry=$registry")
        )?.trim()?.let { versions ->
            try {
                Json.parseToJsonElement(versions)
            } catch (e: Exception) {
                log.warn("Error while parsing all versions for package $packageName", e)
                null
            }?.asJsonArray?.mapNotNull { it.asString }?.also {
                log.info("All versions for package $packageName found locally (${it.size} versions)")
                log.debug("Local versions for $packageName: $it")
            }
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
        } ?: ShellRunner.getInstance(project).execute(
            arrayOf("npm", "v", packageName, "deprecated", "--registry=$registry")
        )?.trim()?.let { deprecationStatus(packageName, it, local = true) }.also { reason ->
            if (reason != null) {
                log.info("Deprecation status for package $packageName found locally: $reason")
            } else {
                log.debug("No deprecation status found for package $packageName")
            }
        }
    }

    fun getPackageLastModified(packageName: String): String? {
        log.info("Getting last modified date for package $packageName")
        val registry = getRegistry(packageName)
        return getBodyAsJSON("$registry/$packageName")?.get("time")?.asJsonObject?.get("modified")?.asString?.also {
            log.info("Last modified date for package $packageName found online: $it")
        } ?: ShellRunner.getInstance(project).execute(
            arrayOf("npm", "v", packageName, "time.modified", "--registry=$registry")
        )?.trim()?.let { it.ifEmpty { null } }.also {
            if (it != null) {
                log.info("Last modified date for package $packageName found locally: $it")
            } else {
                log.warn("Last modified date for package $packageName not found")
            }
        }
    }
}
