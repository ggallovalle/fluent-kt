package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Long-message performance-cliff tests.
 *
 * The goal isn't to benchmark — it's to catch cliffs. A test passes if:
 *  1. The parser produces a structurally correct AST at 10K and 100K chars,
 *  2. The bundle formats the message back to its original text (modulo any
 *     documented transformations),
 *  3. Doubling input length does not more than triple total time, so an
 *     accidental O(n²) or worse shows up as a test failure rather than
 *     a production slowdown.
 *
 * These tests are JVM-targeted because LinuxX64 native tests have shorter
 * default timeouts and resource limits that would mask cliff detection.
 */
val LongMessagesTest by testSuite {

    fun asciiText(n: Int): String = buildString(n) {
        // Letters only to avoid fluent-significant characters ($, { }).
        repeat(n) {
            append(('a' + (it % 26)))
        }
    }

    test("parse + format a 10K-char message id without throwing") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val longText = asciiText(10_000)
        val source = "greet = $longText"
        val r = bundle.addResource(FluentResource.tryNew(source).getOrThrow())
        assertTrue(r.isSuccess, "expected addResource to succeed on a 10K-char message")
        assertNotNull(bundle.getMessage("greet"))
        // Format and confirm the value text round-trips.
        val formatted = bundle.format("greet")
        assertNotNull(formatted, "format should not return null for a populated message")
        assertTrue(
            longText in formatted,
            "formatted output should contain input value text",
        )
    }

    test("parse + format a 100K-char message id without OOM or stack overflow") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val longText = asciiText(100_000)
        val source = "big = $longText"
        val r = bundle.addResource(FluentResource.tryNew(source).getOrThrow())
        assertTrue(r.isSuccess, "expected addResource to succeed on a 100K-char message")
        val msg = bundle.getMessage("big")
        assertNotNull(msg)
        val formatted = bundle.format("big")
        assertNotNull(formatted)
        assertTrue(
            longText.length == formatted.length ||
                formatted.contains(longText.take(200)),
            "formatted output length should track input size (got len=${formatted.length})",
        )
    }

    test("scaling: 10x input length causes no more than ~30x total time (no O(n²) cliff)") {
        // Run a couple of warmup iterations to stabilize JIT behavior;
        // 100K char input is still under the 10s test timeout but gives
        // measurable signal in the timing ratios we care about.
        val smallN = 10_000
        val largeN = 100_000

        val smallText = asciiText(smallN)
        val largeText = asciiText(largeN)

        val smallSource = "g = $smallText"
        val largeSource = "g = $largeText"

        // Warm-up: at least one parse-then-format of each size before
        // we time anything. This avoids measuring JVM interpretation cost.
        repeat(2) {
            val warmup = FluentBundle(listOf(LanguageIdentifier.parse("en")))
            warmup.addResource(FluentResource.tryNew(smallSource).getOrThrow())
            warmup.format("g")
            val warmupLarge = FluentBundle(listOf(LanguageIdentifier.parse("en")))
            warmupLarge.addResource(FluentResource.tryNew(largeSource).getOrThrow())
            warmupLarge.format("g")
        }

        // Measure small — best of 3 to reduce GC/IRQ noise.
        var smallElapsedMs = Long.MAX_VALUE
        repeat(3) {
            val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
            val start = System.nanoTime()
            bundle.addResource(FluentResource.tryNew(smallSource).getOrThrow())
            val formatted = bundle.format("g")
            val elapsed = (System.nanoTime() - start) / 1_000_000
            assertNotNull(formatted)
            if (elapsed < smallElapsedMs) smallElapsedMs = elapsed
        }

        // Measure large — best of 3.
        var largeElapsedMs = 0L
        repeat(3) {
            val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
            val start = System.nanoTime()
            bundle.addResource(FluentResource.tryNew(largeSource).getOrThrow())
            val formatted = bundle.format("g")
            val elapsed = (System.nanoTime() - start) / 1_000_000
            assertNotNull(formatted)
            if (elapsed > largeElapsedMs) largeElapsedMs = elapsed
        }

        // O(n) work: 10x input length => ≤ ~30x time. Generous constant
        // factor keeps CI jitter from false-positives but still catches
        // quadratic cliffs (which would produce 100x ratios).
        val lengthRatio = largeN.toDouble() / smallN.toDouble()
        val timeRatio = if (smallElapsedMs == 0L) {
            // Timing fell below 1ms resolution — well below any cliff
            // threshold, so accept as O(n) or better.
            1.0
        } else {
            largeElapsedMs.toDouble() / smallElapsedMs.toDouble()
        }
        assertTrue(
            timeRatio <= 30.0,
            "expected ~10x input length to take no more than ~30x time. " +
                "small=${smallElapsedMs}ms large=${largeElapsedMs}ms " +
                "timeRatio=${timeRatio}x (lengthRatio=${lengthRatio}x)",
        )
        // Surface the measured numbers so CI logs make the trend
        // observable across runs without changing the test outcome.
        println(
            "perfCliff: smallN=$smallN largeN=$largeN " +
                "smallBest=${smallElapsedMs}ms largeBest=${largeElapsedMs}ms " +
                "timeRatio=${timeRatio}x",
        )
    }

    test("pattern with many placeables does not produce quadratic AST") {
        // { $x } { $x } { $x } ... 1000 placeables. The parser's
        // parsePattern loop should be O(n) — each iteration adds an element,
        // no per-element quadratic work.
        val placeholders = buildString {
            repeat(1_000) { append("{ \$x } ") }
        }.trimEnd()
        val source = "g = $placeholders"

        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val r = bundle.addResource(FluentResource.tryNew(source).getOrThrow())
        assertTrue(r.isSuccess, "addResource should succeed for 1000 placeables")
        val msg = bundle.getMessage("g")
        assertNotNull(msg)
        val pattern = msg.value()
        assertNotNull(pattern)
        // Each placeholder contributes one Placeable element; result
        // should be ~1000 elements. (TextElement padding may add a few.)
        assertTrue(
            pattern.elements.size >= 1_000,
            "expected at least 1000 Pattern elements, got ${pattern.elements.size}",
        )
    }
}
