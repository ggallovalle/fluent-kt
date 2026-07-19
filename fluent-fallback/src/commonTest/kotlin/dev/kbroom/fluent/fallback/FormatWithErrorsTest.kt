package dev.kbroom.fluent.fallback

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.FluentError
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.fluentBundle
import dev.kbroom.fluent.bundle.resolver.ReferenceKind
import dev.kbroom.fluent.bundle.resolver.ResolverError
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the error-collection contract on [Localization.formatWithErrors].
 *
 * Two distinct failure modes:
 *  1. **Missing message id**: the requested id is not in any fallback
 *     bundle — returns `(null, [])`. By design, no error is emitted: the
 *     contract is "the id is not present", not "there was an error resolving it".
 *  2. **Missing message reference**: the requested id exists but its
 *     pattern references another message that's missing — returns
 *     `(text, [FluentError.ResolverError(Reference(MESSAGE, missing-id))])`.
 *     The text is the substituted fallback `{missing}` form.
 */
val FormatWithErrorsTest by testSuite {

    test("missing message id returns null result and no errors") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false) {
            addResource(FluentResource.tryNew("known = hello").getOrThrow())
        }
        val l10n = Localization.simple(bundle)

        val (result, errors) = l10n.formatWithErrors("does-not-exist")
        assertNull(result, "missing message id must return null result, got: $result")
        assertTrue(errors.isEmpty(), "missing-id is not an error, expected empty list, got: $errors")
    }

    test("missing message reference returns text and a ResolverError.Reference") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false) {
            addResource(FluentResource.tryNew("greet = Hello { other }").getOrThrow())
        }
        val l10n = Localization.simple(bundle)

        val (result, errors) = l10n.formatWithErrors("greet")
        assertEquals("Hello {other}", result)
        assertTrue(errors.isNotEmpty(), "expected at least one error for the missing reference, got: $errors")

        val hasReference = errors.any { err ->
            val resolverErr = err as? FluentError.ResolverError ?: return@any false
            val ref = resolverErr.error as? ResolverError.Reference ?: return@any false
            ref.id == "other" && ref.kind == ReferenceKind.MESSAGE
        }
        assertTrue(hasReference, "expected MESSAGE reference to 'other' in $errors")
    }

    test("present message with no missing refs returns the text and no errors") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false) {
            addResource(FluentResource.tryNew("known = hello").getOrThrow())
        }
        val l10n = Localization.simple(bundle)

        val (result, errors) = l10n.formatWithErrors("known")
        assertEquals("hello", result)
        assertTrue(errors.isEmpty(), "present message should not produce errors, got: $errors")
    }
}
