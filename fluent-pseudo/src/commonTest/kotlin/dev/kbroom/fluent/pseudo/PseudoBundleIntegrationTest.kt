package dev.kbroom.fluent.pseudo

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.fluentBundle
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test: wire [createPseudoTransform] into a real [dev.kbroom.fluent.bundle.FluentBundle]
 * via `setTransform` and assert the formatted message carries the pseudo
 * transformation. This guards the bundle integration in addition to the
 * transform-in-isolation tests in [PseudoLocaleTest].
 */
val PseudoBundleIntegrationTest by testSuite {

    test("setTransform with createPseudoTransform(Accented) accents formatted output") {
        val bundle = fluentBundle(
            locales = listOf(LanguageIdentifier.parse("en")),
            useIsolating = false,
        ) {
            addResource(FluentResource.tryNew("greet = Hello").getOrThrow())
            setTransform(createPseudoTransform(PseudoMode.Accented))
        }

        val result = bundle.format("greet")
        assertNotNull(result)
        // Accented mode replaces ASCII letters with diacritic equivalents;
        // "Hello" should at minimum have its 'H' and 'e' transformed.
        assertTrue(
            result.contains("é") || result.contains("É"),
            "expected accented output (é or É); got: $result",
        )
    }

    test("setTransform with createPseudoTransform(Long) pads formatted output") {
        val bundle = fluentBundle(
            locales = listOf(LanguageIdentifier.parse("en")),
            useIsolating = false,
        ) {
            addResource(FluentResource.tryNew("greet = Hello").getOrThrow())
            setTransform(createPseudoTransform(PseudoMode.Long))
        }

        val result = bundle.format("greet")
        assertNotNull(result)
        assertTrue(
            result.length > "Hello".length,
            "Long mode should pad 'Hello' past 5 chars; got len=${result.length}: $result",
        )
    }
}
