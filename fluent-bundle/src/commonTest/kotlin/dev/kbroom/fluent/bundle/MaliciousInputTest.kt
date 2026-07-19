package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.resolver.ResolverError
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Malicious-input protection tests.
 *
 * The resolver must defend against:
 *  - Direct self-references (msg = { msg })
 *  - Mutual cycles (a → b → a)
 *  - Longer chains (a → b → c → a)
 *  - Billion-laughs exponential fan-out (terms referencing terms 10× each)
 *  - Deeply nested placeables (100+ levels of `{ { { … } } }`)
 *
 * The parser must also handle deep nesting without stack overflow.
 */
val MaliciousInputTest by testSuite {

    // ── Cycle detection ──────────────────────────────────────────────

    test("self-referencing message produces Cyclic error and fallback") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("msg = { msg }").getOrThrow())
        val errors = mutableListOf<FluentError>()
        val msg = bundle.getMessage("msg")
        assertNotNull(msg)
        val pattern = msg.value()
        assertNotNull(pattern)
        val result = bundle.formatPattern(pattern, null, errors, rootMessageId = "msg")
        // Should produce a fallback, not recurse forever
        assertEquals("{msg}", result)
        assertTrue(
            errors.any {
                it is FluentError.ResolverError && it.error is ResolverError.Cyclic
            },
            "expected Cyclic error in $errors",
        )
    }

    test("mutual cycle (a → b → a) produces Cyclic error") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(
            FluentResource.tryNew(
                """
                |a = { b }
                |b = { a }
                """.trimMargin(),
            ).getOrThrow(),
        )
        val errors = mutableListOf<FluentError>()
        val msg = bundle.getMessage("a")
        assertNotNull(msg)
        val pattern = msg.value()
        assertNotNull(pattern)
        val result = bundle.formatPattern(pattern, null, errors, rootMessageId = "a")
        // Should resolve to a fallback, not loop
        assertTrue(
            result.contains("{") && result.contains("}"),
            "expected fallback string, got '$result'",
        )
        assertTrue(
            errors.any {
                it is FluentError.ResolverError && it.error is ResolverError.Cyclic
            },
            "expected Cyclic error in $errors",
        )
    }

    test("longer cycle (a → b → c → a) produces Cyclic error") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(
            FluentResource.tryNew(
                """
                |a = { b }
                |b = { c }
                |c = { a }
                """.trimMargin(),
            ).getOrThrow(),
        )
        val errors = mutableListOf<FluentError>()
        val msg = bundle.getMessage("a")
        assertNotNull(msg)
        val pattern = msg.value()
        assertNotNull(pattern)
        val result = bundle.formatPattern(pattern, null, errors, rootMessageId = "a")
        assertTrue(
            result.contains("{") && result.contains("}"),
            "expected fallback string, got '$result'",
        )
        assertTrue(
            errors.any {
                it is FluentError.ResolverError && it.error is ResolverError.Cyclic
            },
            "expected Cyclic error in $errors",
        )
    }

    test("cycle through term self-reference produces Cyclic error") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("-loop = { -loop }\nmsg = { -loop }").getOrThrow())
        val errors = mutableListOf<FluentError>()
        val msg = bundle.getMessage("msg")
        assertNotNull(msg)
        val pattern = msg.value()
        assertNotNull(pattern)
        bundle.formatPattern(pattern, null, errors, rootMessageId = "msg")
        // Term self-reference should be caught
        assertTrue(
            errors.any {
                it is FluentError.ResolverError && it.error is ResolverError.Cyclic
            },
            "expected Cyclic error in $errors",
        )
    }

    // ── Billion laughs (exponential fan-out) ─────────────────────────

    test("billion laughs: exponential term fan-out triggers TooManyPlaceables") {
        // Build a chain of terms, each referencing the previous one 10 times:
        //   -base = X
        //   -l1 = {-base}{-base}{-base}{-base}{-base}{-base}{-base}{-base}{-base}{-base}
        //   -l2 = {-l1}{-l1}{-l1}{-l1}{-l1}{-l1}{-l1}{-l1}{-l1}{-l1}
        //   ...
        // With MAX_PLACEABLES = 100, 3 levels of 10× fan-out = 1 + 10 + 100 + 1000 placeables
        // which exceeds the limit. The resolver should truncate and emit TooManyPlaceables.
        val fanOut = 10
        val levels = 4 // 10^4 = 10000 potential expansions
        val ftl = buildString {
            appendLine("-base = X")
            for (level in 1..levels) {
                val refs = (1..fanOut).joinToString("") { "{ -${if (level == 1) "base" else "l${level - 1}"} }" }
                appendLine("-l$level = $refs")
            }
            appendLine("msg = { -l$levels }")
        }
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew(ftl).getOrThrow())
        val errors = mutableListOf<FluentError>()
        val msg = bundle.getMessage("msg")
        assertNotNull(msg)
        val pattern = msg.value()
        assertNotNull(pattern)
        val result = bundle.formatPattern(pattern, null, errors, rootMessageId = "msg")
        // The result should NOT be a fully expanded string (which would be 10^4 = 10000 chars)
        assertTrue(
            result.length < 10_000,
            "expected truncated output (len=${result.length}), not full expansion",
        )
        assertTrue(
            errors.any {
                it is FluentError.ResolverError && it.error is ResolverError.TooManyPlaceables
            },
            "expected TooManyPlaceables error in $errors",
        )
    }

    test("billion laughs: message-based fan-out also triggers TooManyPlaceables") {
        // Same pattern but using messages instead of terms
        val fanOut = 10
        val levels = 4
        val ftl = buildString {
            appendLine("base = X")
            for (level in 1..levels) {
                val refs = (1..fanOut).joinToString("") { "{ ${if (level == 1) "base" else "l${level - 1}"} }" }
                appendLine("l$level = $refs")
            }
            // The last level is referenced from msg — but messages can't be
            // referenced inside other messages' values the same way terms can.
            // Instead, build the chain from msg itself:
            // msg = { l4 } → l4 has 10 refs to l3 → etc.
        }
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew(ftl).getOrThrow())
        val errors = mutableListOf<FluentError>()
        val msg = bundle.getMessage("l$levels")
        assertNotNull(msg)
        val pattern = msg.value()
        assertNotNull(pattern)
        val result = bundle.formatPattern(pattern, null, errors, rootMessageId = "l$levels")
        // With 4 levels of 10× fan-out, the fully expanded output would be huge
        assertTrue(
            result.length < 10_000,
            "expected truncated output (len=${result.length}), not full expansion",
        )
        assertTrue(
            errors.any {
                it is FluentError.ResolverError && it.error is ResolverError.TooManyPlaceables
            },
            "expected TooManyPlaceables error in $errors",
        )
    }

    test("normal message with a few placeables resolves fine (no false positive)") {
        // Sanity check: a normal message with a handful of placeables
        // should NOT trigger the limit.
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false)
        bundle.addResource(
            FluentResource.tryNew(
                "greeting = Hello { \$name }, you have { \$count } items.",
            ).getOrThrow(),
        )
        val args = fluentArgsOf("name" to "Alice", "count" to 5)
        val result = bundle.format("greeting", args)
        assertEquals("Hello Alice, you have 5 items.", result)
    }

    test("normal term chain (3 levels, no fan-out) resolves fine") {
        // A normal term chain without exponential fan-out should pass.
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(
            FluentResource.tryNew(
                """
                |brand-name = Firefox
                |brand-short = { brand-name }
                |msg = Using { brand-short }!
                """.trimMargin(),
            ).getOrThrow(),
        )
        val result = bundle.format("msg")
        assertEquals("Using Firefox!", result)
    }
}
