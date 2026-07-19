package dev.kbroom.fluent.testing

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.fluentBundle
import dev.kbroom.fluent.fallback.ResourceId
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.resmgr.ResourceManager
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test that uses real Firefox FTL fixtures.
 */
val FixturesTest by testSuite {

    val fixturesPath = "src/jvmTest/resources/fixtures"

    fun loadResource(path: String): FluentResource? {
        val file = File("$fixturesPath/$path.ftl")
        if (!file.exists()) return null
        return FluentResource.tryNew(file.readText()).getOrNull()
    }

    test("browser en-US") {
        val resource = assertNotNull(
            loadResource("en-US/browser/browser"),
            "Should load en-US/browser/browser.ftl",
        )
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en-US"))) {
            addResource(resource)
            addBuiltins()
        }

        val result = bundle.format("browser-main-window-title", null)
        assertNotNull(result, "Should find browser-main-window-title")
    }

    test("browser Polish") {
        val resource = assertNotNull(
            loadResource("pl/browser/browser"),
            "Should load pl/browser/browser.ftl",
        )
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("pl"))) {
            addResource(resource)
            addBuiltins()
        }

        val result = bundle.format("browser-main-window-title", null)
        assertNotNull(result, "Should find browser-main-window-title in Polish")
    }

    test("fallback") {
        val bundle = fluentBundle(
            locales = listOf(
                LanguageIdentifier.parse("pl"),
                LanguageIdentifier.parse("en-US"),
            ),
        ) {
            loadResource("pl/browser/browser")?.let { addResourceOverriding(it) }
            loadResource("en-US/browser/browser")?.let { addResourceOverriding(it) }
            addBuiltins()
        }

        val result = bundle.format("browser-main-window-title", null)
        assertNotNull(result, "Should find browser-main-window-title")
    }

    test("multiple resources") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en-US"))) {
            loadResource("en-US/browser/browser")?.let { addResourceOverriding(it) }
            loadResource("en-US/browser/branding/brandings")?.let { addResourceOverriding(it) }
            addBuiltins()
        }

        assertTrue(bundle.hasMessage("browser-main-window-title"), "Should have browser-main-window-title")
    }

    test("resource manager") {
        val rm = ResourceManager(fixturesPath)
        val bundle = rm.getBundle(
            listOf(LanguageIdentifier.parse("en-US")),
            listOf(ResourceId("browser/browser")),
        )

        val result = bundle.format("browser-main-window-title", null)
        assertNotNull(result, "Should find browser-main-window-title")
    }
}
