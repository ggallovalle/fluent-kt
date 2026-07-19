package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.types.FluentNumber
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.bundle.types.PluralCategory
import dev.kbroom.fluent.bundle.types.fluentValueOf
import dev.kbroom.fluent.bundle.types.getPluralCategory
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("hello = Hello World").getOrThrow())
        }

        val message = bundle.getMessage("hello")
        assertTrue(message != null)
    }

    test("plural category: english one/other") {
        assertEquals(PluralCategory.ONE, getPluralCategory(1.0, "en-US"))
        assertEquals(PluralCategory.OTHER, getPluralCategory(0.0, "en-US"))
        assertEquals(PluralCategory.OTHER, getPluralCategory(2.0, "en-US"))
    }

    test("plural category: slavic one/few/many/other") {
        assertEquals(PluralCategory.ONE, getPluralCategory(1.0, "ru-RU"))
        assertEquals(PluralCategory.FEW, getPluralCategory(2.0, "ru-RU"))
        assertEquals(PluralCategory.MANY, getPluralCategory(5.0, "ru-RU"))
        // CLDR: 0 is "many" in Russian (mod10==0, mod100 not in 11..14)
        assertEquals(PluralCategory.MANY, getPluralCategory(0.0, "ru-RU"))
        assertEquals(PluralCategory.MANY, getPluralCategory(11.0, "ru-RU"))
        // 21 is "one" in Russian (mod10==1, mod100==21, mod100 != 11)
        assertEquals(PluralCategory.ONE, getPluralCategory(21.0, "ru-RU"))
    }

    test("plural category: arabic zero/one/two/few/many/other") {
        assertEquals(PluralCategory.ZERO, getPluralCategory(0.0, "ar"))
        assertEquals(PluralCategory.ONE, getPluralCategory(1.0, "ar"))
        assertEquals(PluralCategory.TWO, getPluralCategory(2.0, "ar"))
        assertEquals(PluralCategory.FEW, getPluralCategory(3.0, "ar"))
        assertEquals(PluralCategory.MANY, getPluralCategory(11.0, "ar"))
        assertEquals(PluralCategory.OTHER, getPluralCategory(100.0, "ar"))
    }

    test("plural category: unknown locale returns OTHER for all values") {
        // The default branch returns OTHER regardless of intValue for any
        // locale that doesn't match en/ru/uk/ar.
        assertEquals(PluralCategory.OTHER, getPluralCategory(1.0, "xx-YY"))
        assertEquals(PluralCategory.OTHER, getPluralCategory(0.0, "xx-YY"))
        assertEquals(PluralCategory.OTHER, getPluralCategory(42.0, "xx-YY"))
    }
}
