package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for FluentBundle operations
 */
val FluentBundleTest by testSuite {

    test("create bundle") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        assertEquals(1, bundle.locales.size)
    }

    test("parse resource") {
        val resource = FluentResource.tryNew("hello = Hello!")
        assertTrue(resource.isSuccess)
    }

    test("add resource") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val resource = FluentResource.tryNew("hello = Hello!").getOrThrow()
        val result = bundle.addResource(resource)
        assertTrue(result.isSuccess)
    }

    test("format message") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val resource = FluentResource.tryNew("hello = Hello!").getOrThrow()
        bundle.addResource(resource)

        val result = bundle.format("hello", null)
        assertEquals("Hello!", result)
    }

    test("message reference") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("foo = Foo").getOrThrow())
        bundle.addResource(FluentResource.tryNew("ref = { foo }").getOrThrow())

        assertEquals("Foo", bundle.format("ref", null))
    }

    test("missing message") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("ref = { missing }").getOrThrow())

        assertEquals("{missing}", bundle.format("ref", null))
    }

    test("resource override") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val res1 = FluentResource.tryNew("key = Value").getOrThrow()
        val res2 = FluentResource.tryNew("key = Value 2").getOrThrow()

        bundle.addResource(res1)
        assertEquals("Value", bundle.format("key", null))

        bundle.addResourceOverriding(res2)
        assertEquals("Value 2", bundle.format("key", null))
    }

    test("term reference") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("-brand = Firefox").getOrThrow())
        bundle.addResource(FluentResource.tryNew("app = Using { -brand }").getOrThrow())

        assertEquals("Using Firefox", bundle.format("app", null))
    }
}
