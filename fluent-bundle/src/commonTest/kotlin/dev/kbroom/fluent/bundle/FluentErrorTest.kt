package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.resolver.ReferenceKind
import dev.kbroom.fluent.bundle.resolver.ResolverError
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [FluentError] — the error type the bundle collects while
 * formatting. Coverage targets each kind and the collection path through
 * [FluentBundle.formatPattern] / [FluentBundle.formatMessage].
 */
val FluentErrorTest by testSuite {

    test("Overriding reports same-id resource on addResource") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("greeting = Hello").getOrThrow())
        val second = FluentResource.tryNew("greeting = Hi").getOrThrow()
        val result = bundle.addResource(second)
        // addResource returns failures when an id is overridden
        assertTrue(result.isFailure, "expected addResource to fail on duplicate id")
        assertNotNull(result.exceptionOrNull())
    }

    test("formatMessage returns null for unknown message id") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("greet = Hi").getOrThrow())
        val result = bundle.format("missing")
        assertNull(result, "expected null for missing message id")
        assertEquals(false, bundle.hasMessage("missing"))
    }

    test("formatPattern collects a ResolverError.Reference on unknown message ref") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("greet = Hi { missing }").getOrThrow())
        val message = bundle.getMessage("greet")
        assertNotNull(message)
        val pattern = message.value()
        assertNotNull(pattern)
        val errors = mutableListOf<FluentError>()
        // The reference is missing so the resolver substitutes the missing
        // message id (rendered as `{missing}`). The important thing for
        // this test is that a ResolverError.Reference(MESSAGE, "missing")
        // was recorded.
        bundle.formatPattern(pattern, null, errors, rootMessageId = "greet")
        val hasMissingMessageRef = errors.any {
            when {
                it !is FluentError.ResolverError -> false
                it.error !is ResolverError.Reference -> false
                else -> it.error.id == "missing" && it.error.kind == ReferenceKind.MESSAGE
            }
        }
        assertTrue(
            hasMissingMessageRef,
            "expected ResolverError.Reference(MESSAGE, missing) in $errors",
        )
    }

    test("FluentError.Overriding carries kind and id") {
        val e = FluentError.Overriding(EntryKind.MESSAGE, "greeting")
        assertEquals(EntryKind.MESSAGE, e.kind)
        assertEquals("greeting", e.id)
    }

    test("FluentError.ParserError wraps the parser error list") {
        val inner = listOf<Any>("first", "second")
        val e = FluentError.ParserError(inner)
        assertEquals(2, e.errors.size)
    }

    test("FluentError.ResolverError wraps a ResolverError kind") {
        val kinds = listOf(
            ResolverError.NoValue,
            ResolverError.Cyclic,
            ResolverError.MissingDefault,
            ResolverError.TooManyPlaceables,
            ResolverError.Reference(ReferenceKind.MESSAGE, "x"),
            ResolverError.Reference(ReferenceKind.TERM, "-x"),
            ResolverError.Reference(ReferenceKind.VARIABLE, "\$x"),
            ResolverError.Reference(ReferenceKind.FUNCTION, "FOO"),
        )
        for (kind in kinds) {
            val wrapped = FluentError.ResolverError(kind)
            assertEquals(kind, wrapped.error)
        }
    }
}
