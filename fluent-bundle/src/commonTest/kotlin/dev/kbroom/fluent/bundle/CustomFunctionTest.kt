package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.types.FluentType
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A test-only `FluentType` whose `asString()` is its email form. */
private class EmailAddress(val local: String, val domain: String) : FluentType {
    override fun duplicate(): FluentType = EmailAddress(local, domain)
    override fun asString(): String = "$local@$domain"
}

/**
 * Custom-function registration contract:
 *  - A function returning `FluentValue.Custom` (a `FluentType`) renders
 *    via `asString()` in the formatted output.
 *  - A function that throws is caught by the resolver, which substitutes
 *    a `FluentValue.Error` so the format call does not propagate the
 *    exception.
 */
val CustomFunctionTest by testSuite {

    test("function returning FluentValue.Custom renders via asString()") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false) {
            addResource(
                FluentResource.tryNew("email = Contact: { CONTACT(\$id) }").getOrThrow(),
            )
            function("CONTACT") { positional, _ ->
                val id = positional.firstOrNull()?.asString().orEmpty()
                FluentValue.Custom(EmailAddress(local = id, domain = "example.com"))
            }
        }

        val result = bundle.format("email", FluentArgs().apply { set("id", "alice") })
        assertEquals("Contact: alice@example.com", result)
    }

    test("function that throws is caught; format call returns error sentinel") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false) {
            addResource(
                FluentResource.tryNew("boom = { KABOOM(\$x) }").getOrThrow(),
            )
            function("KABOOM") { _, _ ->
                throw IllegalStateException("simulated failure")
            }
        }

        // Must not throw.
        val result = bundle.format("boom", FluentArgs().apply { set("x", 1) })
        assertNotNull(result)
        // Resolver substitutes a {Function error: <msg>} form so the
        // formatted text contains the original error message — surfaced
        // through FluentValue.Error.asString() which renders as `{msg}`.
        assertTrue(
            result.contains("simulated failure") || result.contains("Function error"),
            "expected error sentinel in output, got: $result",
        )
    }
}
