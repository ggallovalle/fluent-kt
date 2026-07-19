package dev.kbroom.fluent.resmgr

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.fallback.ResourceId
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ResourceManager — locale resolution, fallback chain, and file
 * loading. The class accepts an `internal open fun readFile` so tests can
 * substitute their own file lookup without touching the filesystem.
 */
val ResourceManagerTest by testSuite {

    /**
     * File-backed resource manager that resolves any path under the given
     * base directory to a content string from a map. Tests construct one
     * with the locale-tag conventions ResourceManager.loadResource probes:
     *   $basePath/${localeTag}/$fileName
     *   $basePath/${locale.language}/$fileName
     *   $basePath/$fileName
     */
    class MockFs(basePath: String, files: Map<String, String>) : ResourceManager(basePath) {
        private val files = files
        override fun readFile(path: String): String? = files[path]
    }

    test("getBundle loads a resource from the locale-tag path and formats it") {
        val fs = MockFs(
            "base",
            mapOf(
                "base/en-US/browser.ftl" to "hello = Hello World\ngreet = Hi { \$name }",
            ),
        )
        val bundle = fs.getBundle(
            locales = listOf(LanguageIdentifier.parse("en-US")),
            resourceIds = listOf(ResourceId("browser")),
        )
        // Isolation marks only appear on bidi text or placeables — "Hello
        // World" is plain ASCII so no marks are inserted.
        assertEquals("Hello World", bundle.format("hello"))
    }

    test("getBundle returns an empty bundle (with builtins) when no .ftl files match") {
        val fs = MockFs("base", emptyMap())
        val bundle = fs.getBundle(
            locales = listOf(LanguageIdentifier.parse("en-US")),
            resourceIds = listOf(ResourceId("missing")),
        )
        assertNull(bundle.formatMessage("missing"))
        assertFalse(bundle.hasMessage("missing"))
        @Suppress("IgnoredReturnValue")
        assertNotNull(bundle.getFunction("NUMBER"))
    }

    test("loadResource falls back to locale.language when locale-tag path is missing") {
        val tried = mutableListOf<String>()
        val fs = object : ResourceManager("base") {
            override fun readFile(path: String): String? {
                tried += path
                return if (path == "base/en/browser.ftl") "hello = Hello" else null
            }
        }
        val bundle = fs.getBundle(
            locales = listOf(LanguageIdentifier.parse("en-US")),
            resourceIds = listOf(ResourceId("browser")),
        )
        assertEquals("Hello", bundle.format("hello"))
        assertTrue(
            "base/en-US/browser.ftl" in tried,
            "expected en-US path probe, got $tried",
        )
        assertTrue(
            "base/en/browser.ftl" in tried,
            "expected language-fallback path probe, got $tried",
        )
    }

    test("loadResource falls back to base/path when neither tag nor language matches") {
        val tried = mutableListOf<String>()
        val fs = object : ResourceManager("base") {
            override fun readFile(path: String): String? {
                tried += path
                return if (path == "base/browser.ftl") "hello = Hi" else null
            }
        }
        val bundle = fs.getBundle(
            locales = listOf(LanguageIdentifier.parse("fr-CA")),
            resourceIds = listOf(ResourceId("browser")),
        )
        assertEquals("Hi", bundle.format("hello"))
        assertTrue("base/fr-CA/browser.ftl" in tried)
        assertTrue("base/fr/browser.ftl" in tried)
        assertTrue("base/browser.ftl" in tried)
    }

    test("getBundles returns one bundle per locale, each scoped to its own files") {
        val fs = MockFs(
            "base",
            mapOf(
                "base/en-US/browser.ftl" to "hello = Howdy",
                "base/en/browser.ftl" to "hello = Hi",
                "base/fr/browser.ftl" to "hello = Bonjour",
            ),
        )
        val bundles = fs.getBundles(
            locales = listOf(
                LanguageIdentifier.parse("en-US"),
                LanguageIdentifier.parse("fr"),
            ),
            resourceIds = listOf(ResourceId("browser")),
        )
        assertEquals(2, bundles.size)
        val enUs = bundles[LanguageIdentifier.parse("en-US")]
        val fr = bundles[LanguageIdentifier.parse("fr")]
        assertNotNull(enUs)
        assertNotNull(fr)
        assertEquals("Howdy", enUs.format("hello"))
        assertEquals("Bonjour", fr.format("hello"))
    }

    test("getBundle combines multiple resource ids in declaration order; later wins") {
        val fs = MockFs(
            "base",
            mapOf(
                "base/en-US/main.ftl" to "shared = Original\nonlyMain = Yes",
                "base/en-US/extra.ftl" to "shared = Override\nonlyExtra = Also",
            ),
        )
        val bundle = fs.getBundle(
            locales = listOf(LanguageIdentifier.parse("en-US")),
            resourceIds = listOf(ResourceId("main"), ResourceId("extra")),
        )
        assertEquals("Override", bundle.format("shared"))
        assertEquals("Yes", bundle.format("onlyMain"))
        assertEquals("Also", bundle.format("onlyExtra"))
    }
}
