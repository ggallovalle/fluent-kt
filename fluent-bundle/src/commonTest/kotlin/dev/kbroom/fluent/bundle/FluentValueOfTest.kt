package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.types.FluentType
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.bundle.types.fluentValueOf
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A test-only `FluentType`. `asString()` returns the formatted amount + currency. */
private class Money(val amount: Double, val currency: String) : FluentType {
    override fun duplicate(): FluentType = Money(amount, currency)
    override fun asString(): String = amount.toString() + " " + currency
}

/**
 * `fluentValueOf(value: Any?)` is the canonical constructor for
 * [FluentValue] from arbitrary Kotlin values. It must:
 *  - Pass [FluentType] through as [FluentValue.Custom].
 *  - Render the wrapped value via [FluentValue.Custom.asString] when the
 *    bundle's resolver substitutes it into a pattern.
 */
val FluentValueOfTest by testSuite {

    test("fluentValueOf wraps a FluentType instance as FluentValue.Custom") {
        val money = Money(amount = 19.99, currency = "USD")
        val value = fluentValueOf(money)
        assertIs<FluentValue.Custom>(value)
        assertEquals(money, value.value)
        assertEquals("19.99 USD", value.asString())
    }

    test("fluentValueOf(FluentType) round-trips through formatMessage") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false) {
            addResource(
                FluentResource.tryNew("price = Total: { \$cost }").getOrThrow(),
            )
        }

        val args = FluentArgs().apply { set("cost", fluentValueOf(Money(42.5, "EUR"))) }
        val result = bundle.format("price", args)
        assertEquals("Total: 42.5 EUR", result)
    }
}
