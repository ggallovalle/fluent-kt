package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val FluentBundleTest by testSuite {

    test("create bundle") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")))
        assertEquals(1, bundle.locales.size)
    }

    test("parse resource") {
        val resource = FluentResource.tryNew("hello = Hello!")
        assertTrue(resource.isSuccess)
    }

    test("add resource") {
        val resource = FluentResource.tryNew("hello = Hello!").getOrThrow()
        val builder = FluentBundleBuilder.builder(listOf(LanguageIdentifier.parse("en")))
        val result = builder.addResource(resource)
        assertTrue(result.isSuccess)
    }

    test("format message") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("hello = Hello!").getOrThrow())
        }
        val result = bundle.format("hello", null)
        assertEquals("Hello!", result)
    }

    test("message reference") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("foo = Foo").getOrThrow())
            addResource(FluentResource.tryNew("ref = { foo }").getOrThrow())
        }
        assertEquals("Foo", bundle.format("ref", null))
    }

    test("missing message") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("ref = { missing }").getOrThrow())
        }
        assertEquals("{missing}", bundle.format("ref", null))
    }

    test("resource override") {
        val res1 = FluentResource.tryNew("key = Value").getOrThrow()
        val res2 = FluentResource.tryNew("key = Value 2").getOrThrow()

        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(res1)
            addResourceOverriding(res2)
        }
        assertEquals("Value 2", bundle.format("key", null))
    }

    test("term reference") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("-brand = Firefox").getOrThrow())
            addResource(FluentResource.tryNew("app = Using { -brand }").getOrThrow())
        }
        assertEquals("Using Firefox", bundle.format("app", null))
    }
}
