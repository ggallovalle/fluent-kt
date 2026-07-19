package dev.kbroom.fluent.fallback

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.fluentBundle
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for fallback localization behavior
 */
val FallbackTest by testSuite {
    test("bundle locale") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en-US"))) {}
        assertEquals(1, bundle.locales.size)
    }

    test("bundle add resource") {
        val resource = FluentResource.tryNew("hello = Hello").getOrThrow()
        val builder = dev.kbroom.fluent.bundle.FluentBundleBuilder.builder(listOf(LanguageIdentifier.parse("en")))
        val result = builder.addResource(resource)
        assertTrue(result.isSuccess)
    }

    test("simple localization") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("hello = Hello World").getOrThrow())
        }

        val result = bundle.format("hello", null)
        assertEquals("Hello World", result)
    }

    test("message not found") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("hello = Hello").getOrThrow())
        }

        val result = bundle.format("missing", null)
        assertEquals(null, result)
    }

    test("empty resource") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("").getOrThrow())
        }

        assertEquals(false, bundle.hasMessage("anything"))
    }

    test("multiple resources") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("hello = Hello").getOrThrow())
            addResource(FluentResource.tryNew("world = World").getOrThrow())
        }

        assertEquals("Hello", bundle.format("hello", null))
        assertEquals("World", bundle.format("world", null))
    }
}
