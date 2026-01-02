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
        for ((key, value) in raw.props.entries.sortedBy { it.key }) { // sorted to ensure method priority
            return Headers(
                when (key) {
                    // _authToken/_authtoken/-authtoken: _authToken=X => "Authorization: Bearer X"
                    "_authToken", "_authtoken", "-authtoken" -> mapOf("Authorization" to "Bearer $value")
                    // _auth: _auth="username:password".base64() => "Authorization: Basic X"
                    "_auth" -> mapOf("Authorization" to "Basic $value")
                    // username => used with password to reproduce `_auth`
                    "username",
                        // _password: password=password.base64() => same as username
                    "_password" -> {
                        val username = if (key == "username") value else raw.props["username"]
                        var password = if (key == "_password") value else raw.props["_password"]
                        if (username == null || password == null) continue // incomplete
                        password = Base64.decode(password).decodeToString() // decode the password
                        mapOf(
                            "Authorization" to "Basic ${Base64.encode("$username:$password".encodeToByteArray())}"
                        )
                    }
                    // email
                    "email" -> continue // unused during authentication
                    // path to mTLS files
                    "cafile", "certfile", "keyfile" -> continue // unsupported for now
                    else -> continue
                }
            )
        }
        return Headers(emptyMap())
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
            ?.also { log.debug("Headers for URL: ${it.values}") }
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
            private val scopeRegex = Regex("""^(@\w+):registry=(\S+)$""")
            private val valueRegex = Regex("""^//(\S+):(\w+)=(.+)$""")
        }

        private var parsed = false
        private val configLocations by lazy {
            // Source: https://docs.npmjs.com/cli/v11/configuring-npm/npmrc#files
            listOfNotNull(
                Path(project.basePath ?: "", ".npmrc").takeIf { project.basePath != null },
                Path(System.getProperty("user.home") ?: "", ".npmrc"),
                Path(System.getenv("PREFIX") ?: "/", "etc", "npmrc"),
                run {
                    val helpOutput = project.service<ShellRunner>().execute(arrayOf("npm", "help")) ?: return@run null
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
            }.asReversed() // ensure the priority is correct
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
                                props = mapOf(key to value)
                            )
                        )
                    } else {
                        val mutableMap = existing.props.toMutableMap()
                        mutableMap[key] = value
                        existing.props = mutableMap
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
        var props: Map<String, String> = emptyMap(),
        var scopes: List<String> = emptyList(),
    ) {
        internal fun belongsTo(url: URI): Boolean {
            return url.host == this.url.host &&
                    (this.url.path.startsWith(url.path) || this.url.path.startsWith(url.path.substringBeforeLast('/')))
        }

        /**
         * Checks if the given URI matches this registry's URL.
         *
         * @param url the URI to check
         * @return true if the URI has the same host and path prefix, false otherwise
         */
        fun matches(url: URI): Boolean {
            return this.url.host == url.host &&
                    (url.path.startsWith(this.url.path) || url.path.substringBeforeLast('/').startsWith(this.url.path))
        }
    }

    class Headers(val values: Map<String, String>) {
        fun asStringsList() = values.entries.flatMap { (key, value) -> listOf(key, value) }
    }
}
