package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals

/**
 * End-to-end resolver assertions, hand-written to pin down specific
 * behaviors and give us a working scaffolding test before scaling to
 * fixture-driven tests via the existing ResolverFixtureTest.
 *
 * The existing ResolverFixtureTest is a no-op (testSuiteHelper only walks
 * the suite tree and never evaluates a TestCase); these tests cover the
 * same ground in a direct, readable way.
 */
val ResolverExecutionTest by testSuite {

    test("formats a simple message") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
        bundle.addResource(FluentResource.tryNew("hello = Hello, World!").getOrThrow())
        assertEquals("Hello, World!", bundle.format("hello"))
    }

    test("formats a message with a variable") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")), useIsolating = false)
        bundle.addResource(FluentResource.tryNew("hi = Hi { \$name }!").getOrThrow())
        val args = FluentArgs()
        args.set("name", "Alice")
        assertEquals("Hi Alice!", bundle.format("hi", args))
    }

    test("formats a message with a term reference") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
        bundle.addResource(FluentResource.tryNew("-brand = Firefox").getOrThrow())
        bundle.addResource(FluentResource.tryNew("using = Using { -brand }").getOrThrow())
        assertEquals("Using Firefox", bundle.format("using"))
    }

    test("falls back to literal id when a message reference is missing") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
        bundle.addResource(FluentResource.tryNew("ref = { missing }").getOrThrow())
        assertEquals("{missing}", bundle.format("ref"))
    }

    test("falls back to literal id when a term reference is missing") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
        bundle.addResource(FluentResource.tryNew("ref = { -missing }").getOrThrow())
        assertEquals("{-missing}", bundle.format("ref"))
    }

    test("formats an attribute by message.attribute query") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
        bundle.addResource(FluentResource.tryNew("foo = Foo\n    .attr = Foo Attribute").getOrThrow())
        val message = bundle.getMessage("foo")!!
        assertEquals("Foo Attribute", bundle.formatPattern(message.getAttributeValue("attr")!!))
    }

    test("uses default variant when selector has no match") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
        bundle.addResource(
            FluentResource.tryNew(
                """
                select = { ${'$'}selector ->
                    [a] A
                   *[b] B
                }
                """.trimIndent(),
            ).getOrThrow(),
        )
        val args = FluentArgs()
        args.set("selector", "c")
        assertEquals("B", bundle.format("select", args))
    }

    test("matches number selector in select expression") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
        bundle.addResource(
            FluentResource.tryNew(
                """
                select = { ${'$'}selector ->
                    [0] A
                   *[1] B
                }
                """.trimIndent(),
            ).getOrThrow(),
        )
        val args = FluentArgs()
        args.set("selector", 0)
        assertEquals("A", bundle.format("select", args))
    }
}
