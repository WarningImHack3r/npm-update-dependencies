package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.URI
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

class NPMConfigReaderTests : BasePlatformTestCase() {
    val resolverField: Field = NPMConfigReader::class.java.getDeclaredField("resolver")
        .apply { isAccessible = true }
    val parseConfigMethod: Method =
        NPMConfigReader.NPMConfigResolver::class.java.getDeclaredMethod("parseConfig", String::class.java)
            .apply { isAccessible = true }
    val registriesField: Field = NPMConfigReader.NPMConfigResolver::class.java.getDeclaredField("registries")
        .apply { isAccessible = true }
    val parsedField: Field = NPMConfigReader.NPMConfigResolver::class.java.getDeclaredField("parsed")
        .apply { isAccessible = true }

    override fun getTestDataPath() = "src/test/testdata/configReader"

    private fun getPopulatedRegistries(rawContent: String): List<NPMConfigReader.RawRegistry> {
        val resolver = NPMConfigReader.NPMConfigResolver(project)
        val tempFile = createTempFile()
        try {
            tempFile.writeText(rawContent)
            parseConfigMethod.invoke(resolver, tempFile.toString())
            @Suppress("UNCHECKED_CAST")
            return registriesField.get(resolver) as List<NPMConfigReader.RawRegistry>
        } finally {
            tempFile.deleteIfExists()
        }
    }

    private fun assertHeadersMatching(
        url: String,
        expectedHeaders: Map<String, String>,
        vararg initialRegistries: NPMConfigReader.RawRegistry
    ) {
        val reader = NPMConfigReader(project)
        val resolver = resolverField.get(reader) as NPMConfigReader.NPMConfigResolver
        registriesField.set(resolver, initialRegistries.toList())
        parsedField.set(resolver, true)
        assertContainsElements(expectedHeaders.values, reader.getHeaders(URI(url)).map.values)
    }

    fun testExampleConfigParsing() {
        // source (slightly modified): https://docs.npmjs.com/cli/v11/configuring-npm/npmrc#auth-related-configuration
        val raw = """
        ; bad config
        _authToken=MYTOKEN4

        ; good config
        @myorg:registry=https://somewhere-else.com/myorg
        @another:registry=https://somewhere-else.com/another
        //registry.npmjs.org/:_authToken=MYTOKEN

        ; would apply to both @myorg and @another
        //somewhere-else.com/:_authToken=MYTOKEN3

        ; would apply only to @myorg
        //somewhere-else.com/myorg/:_authToken=MYTOKEN1

        ; would apply only to @another
        //somewhere-else.com/another/:_authToken=MYTOKEN2
        """.trimIndent()
        val registries = getPopulatedRegistries(raw)
        assertSize(3, registries)
        assertContainsElements(
            registries,
            NPMConfigReader.RawRegistry(
                url = URI("https://registry.npmjs.com"),
                props = NPMConfigReader.RawRegistry.Properties().apply {
                    authToken = "MYTOKEN"
                }
            ),
            NPMConfigReader.RawRegistry(
                URI("https://somewhere-else.com/myorg"),
                listOf("@myorg"),
                NPMConfigReader.RawRegistry.Properties().apply {
                    authToken = "MYTOKEN1"
                }
            ),
            NPMConfigReader.RawRegistry(
                URI("https://somewhere-else.com/another"),
                listOf("@another"),
                NPMConfigReader.RawRegistry.Properties().apply {
                    authToken = "MYTOKEN2"
                }
            )
        )
    }

    fun testGetHeadersMatching() {
        assertHeadersMatching(
            "https://registry.npmjs.org/vite",
            mapOf("Authorization" to "Bearer MYTOKEN"),
            NPMConfigReader.RawRegistry(
                url = URI("https://registry.npmjs.org"),
                props = NPMConfigReader.RawRegistry.Properties(
                    authToken = "MYTOKEN"
                )
            )
        )
    }
}
