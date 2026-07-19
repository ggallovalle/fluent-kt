package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val BuiltinDateTimeTest by testSuite {

    test("DATETIME formats with default style") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = Today is { DATETIME(\$when) }")
            builtins()
        }
        val epochMillis = 1_700_000_000_000L
        val result = bundle.format("msg", FluentArgs().apply { set("when", epochMillis) })
        checkNotNull(result)
        assertTrue(result.startsWith("Today is "))
        assertTrue(result.length > "Today is ".length, "got: $result")
    }

    test("DATETIME accepts positional dateStyle as second positional arg") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = { DATETIME(\$when, \"long\") }")
            builtins()
        }
        val result = bundle.format("msg", FluentArgs().apply { set("when", 1_700_000_000_000L) })
        checkNotNull(result)
        // "long" dateStyle on en produces something like "November 14, 2023"
        assertTrue(result.contains("2023"), "expected year in long-form date; got: $result")
    }

    test("DATETIME accepts named dateStyle argument") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = { DATETIME(\$when, dateStyle: \"long\") }")
            builtins()
        }
        val result = bundle.format("msg", FluentArgs().apply { set("when", 1_700_000_000_000L) })
        checkNotNull(result)
        assertTrue(result.contains("2023"), "expected year in long-form date; got: $result")
    }

    test("DATETIME returns error when value isn't a number") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = { DATETIME(\$when) }")
            builtins()
        }
        val result = bundle.format("msg", FluentArgs().apply { set("when", "not a number") })
        checkNotNull(result)
        assertTrue(result.contains("{"), "expected error sentinel in output; got: $result")
    }

    test("DATE formats a date value") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = { DATE(\$when, \"short\") }")
            builtins()
        }
        val result = bundle.format("msg", FluentArgs().apply { set("when", 1_700_000_000_000L) })
        checkNotNull(result)
        assertTrue(result.contains("/") || result.contains("-") || result.contains("2023"))
    }

    test("TIME formats a time value") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = { TIME(\$when) }")
            builtins()
        }
        val result = bundle.format("msg", FluentArgs().apply { set("when", 1_700_000_000_000L) })
        checkNotNull(result)
        assertTrue(result.contains(":"))
    }

    test("LIST formats a list of values with conjunction") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = { LIST(\"a\", \"b\", \"c\") }")
            builtins()
        }
        val result = bundle.format("msg")
        assertEquals("a, b, and c", result)
    }

    test("LIST supports named type/style overrides") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = { LIST(\"a\", \"b\", type: \"disjunction\") }")
            builtins()
        }
        val result = bundle.format("msg")
        // 2-element disjunction: "a or b" (no Oxford comma).
        assertEquals("a or b", result)
    }

    test("LIST disjunction with 3+ items uses Oxford comma") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = { LIST(\"a\", \"b\", \"c\", type: \"disjunction\") }")
            builtins()
        }
        val result = bundle.format("msg")
        assertEquals("a, b, or c", result)
    }

    test("PLURAL ordinal: English 1st -> one, 2nd -> two, 3rd -> few, 11th -> other") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            // fluent-kt parses true/false as variable references (no BooleanLiteral AST node yet),
            // so we pass ordinal: "true" as a string — optBoolean() coerces "true"/"false" strings.
            resource("msg = { PLURAL(\$n, ordinal: \"true\") }")
            builtins()
        }

        assertEquals("one", bundle.format("msg", FluentArgs().apply { set("n", 1) }))
        assertEquals("two", bundle.format("msg", FluentArgs().apply { set("n", 2) }))
        assertEquals("few", bundle.format("msg", FluentArgs().apply { set("n", 3) }))
        assertEquals("other", bundle.format("msg", FluentArgs().apply { set("n", 11) }))
        assertEquals("other", bundle.format("msg", FluentArgs().apply { set("n", 100) }))
    }

    test("PLURAL without ordinal returns cardinal categories") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = { PLURAL(\$n) }")
            builtins()
        }

        assertEquals("one", bundle.format("msg", FluentArgs().apply { set("n", 1) }))
        assertEquals("other", bundle.format("msg", FluentArgs().apply { set("n", 2) }))
        assertEquals("other", bundle.format("msg", FluentArgs().apply { set("n", 11) }))
    }
}
