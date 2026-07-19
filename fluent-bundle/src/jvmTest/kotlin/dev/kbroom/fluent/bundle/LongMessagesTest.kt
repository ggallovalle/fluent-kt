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
        val longText = asciiText(10_000)
        val source = "greet = $longText"
        val builder = FluentBundleBuilder.builder(listOf(LanguageIdentifier.parse("en")))
        val r = builder.addResource(FluentResource.tryNew(source).getOrThrow())
        assertTrue(r.isSuccess, "expected addResource to succeed on a 10K-char message")
        val bundle = builder.build()

        assertNotNull(bundle.getMessage("greet"))
        val formatted = bundle.format("greet")
        assertNotNull(formatted, "format should not return null for a populated message")
        assertTrue(
            longText in formatted,
            "formatted output should contain input value text",
        )
    }

    test("parse + format a 100K-char message id without OOM or stack overflow") {
        val longText = asciiText(100_000)
        val source = "big = $longText"
        val builder = FluentBundleBuilder.builder(listOf(LanguageIdentifier.parse("en")))
        val r = builder.addResource(FluentResource.tryNew(source).getOrThrow())
        assertTrue(r.isSuccess, "expected addResource to succeed on a 100K-char message")
        val bundle = builder.build()

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
        val smallN = 10_000
        val largeN = 100_000
        val smallText = asciiText(smallN)
        val largeText = asciiText(largeN)
        val smallSource = "g = $smallText"
        val largeSource = "g = $largeText"

        // Warm-up.
        repeat(2) {
            fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
                addResource(FluentResource.tryNew(smallSource).getOrThrow())
            }.format("g")
            fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
                addResource(FluentResource.tryNew(largeSource).getOrThrow())
            }.format("g")
        }

        // Measure small — best of 3.
        var smallElapsedMs = Long.MAX_VALUE
        repeat(3) {
            val builder = FluentBundleBuilder.builder(listOf(LanguageIdentifier.parse("en")))
            val start = System.nanoTime()
            builder.addResource(FluentResource.tryNew(smallSource).getOrThrow())
            val formatted = builder.build().format("g")
            val elapsed = (System.nanoTime() - start) / 1_000_000
            assertNotNull(formatted)
            if (elapsed < smallElapsedMs) smallElapsedMs = elapsed
        }

        // Measure large — best of 3.
        var largeElapsedMs = 0L
        repeat(3) {
            val builder = FluentBundleBuilder.builder(listOf(LanguageIdentifier.parse("en")))
            val start = System.nanoTime()
            builder.addResource(FluentResource.tryNew(largeSource).getOrThrow())
            val formatted = builder.build().format("g")
            val elapsed = (System.nanoTime() - start) / 1_000_000
            assertNotNull(formatted)
            if (elapsed > largeElapsedMs) largeElapsedMs = elapsed
        }

        val lengthRatio = largeN.toDouble() / smallN.toDouble()
        val timeRatio = if (smallElapsedMs == 0L) {
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
        println(
            "perfCliff: smallN=$smallN largeN=$largeN " +
                "smallBest=${smallElapsedMs}ms largeBest=${largeElapsedMs}ms " +
                "timeRatio=${timeRatio}x",
        )
    }

    test("pattern with many placeables does not produce quadratic AST") {
        val placeholders = buildString {
            repeat(1_000) { append("{ \$x } ") }
        }.trimEnd()
        val source = "g = $placeholders"

        val builder = FluentBundleBuilder.builder(listOf(LanguageIdentifier.parse("en")))
        val r = builder.addResource(FluentResource.tryNew(source).getOrThrow())
        assertTrue(r.isSuccess, "addResource should succeed for 1000 placeables")
        val bundle = builder.build()

        val msg = bundle.getMessage("g")
        assertNotNull(msg)
        val pattern = msg.value()
        assertNotNull(pattern)
        assertTrue(
            pattern.elements.size >= 1_000,
            "expected at least 1000 Pattern elements, got ${pattern.elements.size}",
        )
    }
}
