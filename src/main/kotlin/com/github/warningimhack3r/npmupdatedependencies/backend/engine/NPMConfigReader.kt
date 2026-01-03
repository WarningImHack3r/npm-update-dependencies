package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.NUDConstants.NPMJS_REGISTRY
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.applyIf
import com.intellij.util.io.exists
import java.io.File
import java.net.URI
import kotlin.io.encoding.Base64
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class NPMConfigReader(project: Project) {
    companion object {
        private val log = logger<NPMConfigReader>()
    }

    private val resolver = NPMConfigResolver(project)

    /**
     * Returns the headers map required to authenticate to a given registry
     * with an HTTP request.
     *
     * @param raw the raw registry configuration, parsed from the npm config file
     * @return the headers map required to authenticate to the registry
     */
    private fun headersForRegistry(raw: RawRegistry): Headers {
        val headers = mutableMapOf<String, String>()
        raw.props.authToken?.let {
            headers["Authorization"] = "Bearer $it"
        } ?: raw.props.auth?.let {
            headers["Authorization"] = "Basic $it"
        } ?: run {
            val username = raw.props.username
            val password = raw.props.password
            if (username == null || password == null) return@run // incomplete
            try {
                val decodedPassword = Base64.decode(password).decodeToString() // password is stored in base64
                headers["Authorization"] = "Basic ${Base64.encode("$username:$decodedPassword".encodeToByteArray())}"
            } catch (e: Exception) {
                log.warn("Error while decoding username/password combination", e)
            }
        }
        // for the other properties:
        // - email is unused during authentication
        // - mTLS files are unsupported for now
        return Headers(headers)
    }

    /**
     * Retrieves and returns the headers map required to authenticate to a given registry
     * URL with an HTTP request.
     *
     * @param uri the URI of the registry to authenticate to
     * @return the headers map required to authenticate to the registry
     */
    fun getHeaders(uri: URI): Headers {
        val npmRegistryUri = URI(NPMJS_REGISTRY)
        return resolver.getRawRegistries()
            .find { registry ->
                registry.matches(
                    URI(
                        uri.scheme,
                        uri.host.applyIf(uri.host == npmRegistryUri.host.replace(Regex("""\.\w+$"""), ".org")) {
                            npmRegistryUri.host
                        },
                        uri.path,
                        uri.fragment
                    ).also {
                        log.debug("Input URL while getting headers: $it")
                    }
                )
            }
            ?.let { headersForRegistry(it) }
            ?.also { log.debug("Headers for URL: ${it.map}") }
            ?: Headers(emptyMap()).also { log.debug("No matching registry found") }
    }

    /**
     * Returns all the registries parsed from the npm config files.
     *
     * @return a collection of registries
     */
    fun getAllRegistries(): Collection<Registry> {
        return resolver.getRawRegistries().flatMap { raw ->
            raw.scopes.map { scope ->
                Registry.ScopedRegistry(raw.url.toString(), scope)
            }.ifEmpty { listOf(Registry.RegularRegistry(raw.url.toString())) }
        }
    }

    internal class NPMConfigResolver(val project: Project) {
        companion object {
            private val log = logger<NPMConfigResolver>()
            private val npmHelpPathRegex = Regex("""^npm@[\d.]+ \S+$""")
            private val scopeRegex = Regex("""^(@\S+):registry=(\S+)$""")
            private val valueRegex = Regex("""^//(\S+):(\w+)=(\S+)$""")
        }

        private var parsed = false
        private val configLocations by lazy {
            // Source: https://docs.npmjs.com/cli/v11/configuring-npm/npmrc#files
            listOfNotNull(
                // project
                Path(project.basePath ?: "", ".npmrc").takeIf { project.basePath != null },
                // user
                Path(System.getProperty("user.home") ?: "", ".npmrc"),
                // global
                Path(System.getenv("PREFIX") ?: "", "etc", "npmrc").takeIf { System.getenv("PREFIX") != null },
                // builtin
                run outer@{
                    val helpOutput = project.service<ShellRunner>().execute(arrayOf("npm", "help")) ?: run {
                        log.debug("Unable to get help output, ignoring global npm configuration")
                        return@outer null
                    }
                    // after trimming, it's effectively always the last line, but we're ensured it's correct with the regex
                    val rawPath = helpOutput.trim().lines().firstOrNull { line ->
                        npmHelpPathRegex.matches(line)
                    }?.split(" ")?.get(1)
                    rawPath?.let { path -> Path(path, "npmrc") }
                }
            ).filter { path ->
                val exists = path.exists()
                if (!exists) log.debug("npm config location not found: $path")
                exists
            }.map { path -> path.toAbsolutePath().toString() }.also {
                log.debug("npm config locations found: ${it.joinToString(", ")}")
            }.asReversed() // ensure the priority is correct and it goes from wider to narrower
        }
        private val registries = mutableListOf<RawRegistry>()

        private fun ensureConfigsParsed() {
            if (!parsed) parseConfigs()
        }

        private fun parseConfigs() {
            for (path in configLocations) {
                parseConfig(path)
            }
            parsed = true
        }

        /**
         * Parses an npm config file at the given path.
         *
         * Conforms to npm 9+, meaning it doesn't parse auth info when it's
         * not bound to a registry.
         *
         * @param path the path to the npm config file
         */
        private fun parseConfig(path: String) {
            val npmRegistryUri = URI(NPMJS_REGISTRY)
            val validLines = File(path).readLines()
                .map { it.substringBefore('#').substringBefore(';').trim() }
                .filter { it.isNotBlank() }
            validLines.forEach { line ->
                scopeRegex.find(line)?.destructured?.let { (scope, url) ->
                    registries.add(
                        RawRegistry(
                            url = URI(url),
                            scopes = listOf(scope)
                        )
                    )
                }
                valueRegex.find(line)?.destructured?.let { (fragment, key, value) ->
                    var parsed =
                        URI("https://$fragment") // irrelevant scheme as it won't get saved; only for comparison purposes
                    if (parsed.host == npmRegistryUri.host
                        || parsed.host == npmRegistryUri.host.replace(Regex("""\.\w+$"""), ".org")
                    ) {
                        parsed = npmRegistryUri
                    }
                    val existing = registries.find { existing ->
                        existing.belongsTo(parsed)
                    }
                    if (existing == null) {
                        registries.add(
                            RawRegistry(
                                url = parsed,
                                props = RawRegistry.Properties().withRaw(key, value)
                            )
                        )
                    } else {
                        existing.props.withRaw(key, value)
                    }
                }
                // any other line is not relevant
            }
        }

        /**
         * Retrieves and returns the list of registries parsed from the npm config files.
         *
         * @return a list of raw registries.
         */
        fun getRawRegistries(): List<RawRegistry> {
            ensureConfigsParsed()
            return registries.also {
                log.debug("Parsed registries, ${registries.size} registries found:\n${registries.joinToString("\n")}")
            }
        }
    }

    sealed interface Registry {
        data class RegularRegistry(override val url: String) : Registry

        data class ScopedRegistry(override val url: String, val scope: String) : Registry

        val url: String
    }

    internal data class RawRegistry(
        val url: URI,
        val scopes: List<String> = emptyList(),
        var props: Properties = Properties()
    ) {
        /**
         * Represents the properties associated with a raw registry configuration.
         *
         * Some additional properties can be missing as they are not relevant in this context.
         */
        data class Properties(
            var authToken: String? = null,
            var auth: String? = null,
            var username: String? = null,
            var password: String? = null,
            var email: String? = null,
            var caFile: String? = null,
            var certFile: String? = null,
            var keyFile: String? = null
        ) {
            /**
             * Populates the appropriate field of this Properties object with the value, based on the raw key.
             *
             * If the raw key is not recognized, the value is discarded.
             *
             * @param rawKey The raw key from the configuration file
             * @param value The value associated with the raw key
             * @return The updated Properties object
             */
            fun withRaw(rawKey: String, value: String): Properties {
                when (rawKey) {
                    "_authToken", "_authtoken", "-authtoken" -> this.authToken = value
                    "_auth" -> this.auth = value
                    "username" -> this.username = value
                    "_password" -> this.password = value
                    "email" -> this.email = value
                    "cafile" -> this.caFile = value
                    "certfile" -> this.certFile = value
                    "keyfile" -> this.keyFile = value
                    else -> {
                        // unsupported property
                    }
                }
                return this
            }
        }

        /**
         * Checks if this registry's URL is a subchild of the given URI.
         *
         * This method is meant to be the opposite of the `matches` method.
         *
         * @param url the URI to check
         * @return true if this registry's URL is a subchild of the given URI, false otherwise
         */
        internal fun belongsTo(url: URI): Boolean {
            return url.host == this.url.host &&
                    (this.url.path.startsWith(url.path) || this.url.path.startsWith(url.path.removeSuffix("/"))) // meant to make `/path` match with `/path/`; the first part of the "or" condition already handles the majority of the cases
        }

        /**
         * Checks if the given URI has the same host and path prefix as this registry's URL.
         *
         * @param url the URI to check
         * @return true if the URI has the same host and path prefix, false otherwise
         */
        fun matches(url: URI): Boolean {
            return this.url.host == url.host &&
                    (url.path.startsWith(this.url.path) || url.path.removeSuffix("/").startsWith(this.url.path))
        }
    }

    class Headers(val map: Map<String, String>) {
        fun asStringsList() = map.entries.flatMap { (key, value) -> listOf(key, value) }
    }
}
