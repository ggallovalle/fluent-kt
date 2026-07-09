package dev.kbroom.fluent.bundle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.bundle.types.FluentNumber
import dev.kbroom.fluent.bundle.types.fluentValueOf
import de.infix.testBalloon.framework.core.testSuite

/**
 * Tests for FluentValue types and conversions
 */
val TypesTest by testSuite {

    test("fluent value string") {
        val value = FluentValue.Str("hello")
        assertEquals("hello", value.asString())
    }

    test("fluent value number") {
        val value = FluentValue.Number(FluentNumber(42.0))
        assertEquals("42", value.asString())
    }

    test("fluent value number decimal") {
        val value = FluentValue.Number(FluentNumber(3.14))
        assertTrue(value.asString().startsWith("3.14"))
    }

    test("fluent value none") {
        val value = FluentValue.None
        assertEquals("", value.asString())
    }

    test("fluent number creation") {
        val num = FluentNumber(42.0)
        assertEquals(42.0, num.value)
    }

    test("fluent args set") {
        val args = FluentArgs()
        args.set("name", "World")

        assertTrue(args.contains("name"))
    }

    test("fluent args get") {
        val args = FluentArgs()
        args.set("name", "World")

        val value = args.get("name")
        assertTrue(value is FluentValue.Str)
    }

    test("value from number") {
        val value = fluentValueOf(42)
        assertTrue(value is FluentValue.Number)
    }

    test("value from string") {
        val value = fluentValueOf("test")
        assertTrue(value is FluentValue.Str)
    }

    test("value from double") {
        val value = fluentValueOf(3.14)
        assertTrue(value is FluentValue.Number)
    }

    test("optional value present") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("hello = Hello World").getOrThrow())

        val message = bundle.getMessage("hello")
        assertTrue(message != null)
    }
}
