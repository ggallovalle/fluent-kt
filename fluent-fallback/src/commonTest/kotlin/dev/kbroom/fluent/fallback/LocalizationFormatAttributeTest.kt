package dev.kbroom.fluent.fallback

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.fluentArgsOf
import dev.kbroom.fluent.bundle.fluentBundle
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertNull

val LocalizationFormatAttributeTest by testSuite {

    test("Localization.formatAttribute uses AST path, not string concat") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false) {
            addResource(
                FluentResource.tryNew(
                    """
                    greet = Default
                        .formal = Good day, { ${'$'}name }
                        .informal = Hey { ${'$'}name }
                    """.trimIndent(),
                ).getOrThrow(),
            )
        }
        val l10n = Localization.simple(bundle)

        // Attribute lookup uses FluentMessage.getAttributeValue, which resolves
        // the attribute pattern — not "greet.formal" string concat against getMessage().
        val result = l10n.formatAttribute("greet", "formal", fluentArgsOf("name" to "World"))
        assertEquals("Good day, World", result)
    }

    test("Localization.formatAttribute returns null when attribute is missing") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false) {
            addResource(FluentResource.tryNew("greet = Default").getOrThrow())
        }
        val l10n = Localization.simple(bundle)

        assertNull(l10n.formatAttribute("greet", "nope"))
    }

    test("Localization.formatAttribute returns null when message is missing") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false) {}
        val l10n = Localization.simple(bundle)

        assertNull(l10n.formatAttribute("nope", "nope"))
    }
}
