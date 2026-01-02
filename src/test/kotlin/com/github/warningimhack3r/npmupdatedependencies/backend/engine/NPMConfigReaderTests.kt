package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.URI
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

class NPMConfigReaderTests : BasePlatformTestCase() {
    val parseConfigMethod: Method =
        NPMConfigReader.NPMConfigResolver::class.java.getDeclaredMethod("parseConfig", String::class.java)
            .apply { isAccessible = true }
    val registriesField: Field = NPMConfigReader.NPMConfigResolver::class.java.getDeclaredField("registries")
        .apply { isAccessible = true }

    override fun getTestDataPath() = "src/test/testdata/configReader"

    @Suppress("UNCHECKED_CAST")
    private fun getPopulatedRegistries(rawContent: String): List<NPMConfigReader.RawRegistry> {
        val resolver = NPMConfigReader.NPMConfigResolver(project)
        val tempFile = createTempFile()
        try {
            tempFile.writeText(rawContent)
            parseConfigMethod.invoke(resolver, tempFile.toString())
            return registriesField.get(resolver) as List<NPMConfigReader.RawRegistry>
        } finally {
            tempFile.deleteIfExists()
        }
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
                URI("https://registry.npmjs.com"),
                mapOf(
                    "_authToken" to "MYTOKEN"
                )
            ),
            NPMConfigReader.RawRegistry(
                URI("https://somewhere-else.com/myorg"),
                mapOf(
                    "_authToken" to "MYTOKEN1"
                ),
                listOf("@myorg")
            ),
            NPMConfigReader.RawRegistry(
                URI("https://somewhere-else.com/another"),
                mapOf(
                    "_authToken" to "MYTOKEN2"
                ),
                listOf("@another")
            )
        )
    }
}
