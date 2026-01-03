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
        // Test 1: Default working case
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

        // Test 2: Root registry should match any package path
        assertHeadersMatching(
            "https://registry.npmjs.org/some/deep/package",
            mapOf("Authorization" to "Bearer ROOTTOKEN"),
            NPMConfigReader.RawRegistry(
                url = URI("https://registry.npmjs.org/"),
                props = NPMConfigReader.RawRegistry.Properties(authToken = "ROOTTOKEN")
            )
        )

        // Test 3: Scoped registry should match packages under its path
        assertHeadersMatching(
            "https://custom.registry.com/npm/private/package",
            mapOf("Authorization" to "Bearer SCOPEDTOKEN"),
            NPMConfigReader.RawRegistry(
                url = URI("https://custom.registry.com/npm/private"),
                props = NPMConfigReader.RawRegistry.Properties(authToken = "SCOPEDTOKEN")
            )
        )

        // Test 4: Should match more specific registry over root
        assertHeadersMatching(
            "https://registry.npmjs.org/private/package",
            mapOf("Authorization" to "Bearer PRIVATETOKEN"),
            NPMConfigReader.RawRegistry(
                url = URI("https://registry.npmjs.org/"),
                props = NPMConfigReader.RawRegistry.Properties(authToken = "ROOTTOKEN")
            ),
            NPMConfigReader.RawRegistry(
                url = URI("https://registry.npmjs.org/private"),
                props = NPMConfigReader.RawRegistry.Properties(authToken = "PRIVATETOKEN")
            )
        )

        // Test 5: Different host should not match
        assertHeadersMatching(
            "https://different.registry.com/package",
            emptyMap(),
            NPMConfigReader.RawRegistry(
                url = URI("https://registry.npmjs.org/"),
                props = NPMConfigReader.RawRegistry.Properties(authToken = "TOKEN")
            )
        )

        // Test 6: Multi-segment path matching
        assertHeadersMatching(
            "https://custom.registry.com/npm/public/scoped/package",
            mapOf("Authorization" to "Bearer MULTITOKEN"),
            NPMConfigReader.RawRegistry(
                url = URI("https://custom.registry.com/npm/public"),
                props = NPMConfigReader.RawRegistry.Properties(authToken = "MULTITOKEN")
            )
        )
    }

    fun testBelongsTo() {
        // Test 1: Registry at /private belongs to root
        val registry1 = NPMConfigReader.RawRegistry(url = URI("https://registry.npmjs.org/private"))
        assertTrue(registry1.belongsTo(URI("https://registry.npmjs.org/")))

        // Test 2: Registry at /npm/private belongs to /npm
        val registry2 = NPMConfigReader.RawRegistry(url = URI("https://custom.com/npm/private"))
        assertTrue(registry2.belongsTo(URI("https://custom.com/npm")))

        // Test 3: Registry at /npm should NOT belong to /private
        val registry3 = NPMConfigReader.RawRegistry(url = URI("https://custom.com/npm"))
        assertFalse(registry3.belongsTo(URI("https://custom.com/private")))

        // Test 4: Registry at root belongs to itself
        val registry4 = NPMConfigReader.RawRegistry(url = URI("https://registry.npmjs.org/"))
        assertTrue(registry4.belongsTo(URI("https://registry.npmjs.org/")))

        // Test 5: Different hosts never belong
        val registry5 = NPMConfigReader.RawRegistry(url = URI("https://registry.npmjs.org/"))
        assertFalse(registry5.belongsTo(URI("https://different.com/")))

        // Test 6: Trailing slash handling - /path belongs to /path/
        val registry6 = NPMConfigReader.RawRegistry(url = URI("https://custom.com/path"))
        assertTrue(registry6.belongsTo(URI("https://custom.com/path/")))
    }
}
