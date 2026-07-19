package dev.kbroom.fluent.fallback

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for fallback localization behavior
 */
val FallbackTest by testSuite {
    test("bundle locale") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
        assertEquals(1, bundle.locales.size)
    }

    test("bundle add resource") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val resource = FluentResource.tryNew("hello = Hello").getOrThrow()
        val result = bundle.addResource(resource)
        assertTrue(result.isSuccess)
    }

    test("simple localization") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("hello = Hello World").getOrThrow())

        val result = bundle.format("hello", null)
        assertEquals("Hello World", result)
    }

    test("message not found") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("hello = Hello").getOrThrow())

        val result = bundle.format("missing", null)
        assertEquals(null, result)
    }

    test("empty resource") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val resource = FluentResource.tryNew("").getOrThrow()
        bundle.addResource(resource)

        assertTrue(!bundle.hasMessage("anything"))
    }

    test("multiple resources") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("hello = Hello").getOrThrow())
        bundle.addResource(FluentResource.tryNew("world = World").getOrThrow())

        assertEquals("Hello", bundle.format("hello", null))
        assertEquals("World", bundle.format("world", null))
    }
}
