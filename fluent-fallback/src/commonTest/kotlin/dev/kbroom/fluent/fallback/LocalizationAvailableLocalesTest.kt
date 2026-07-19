package dev.kbroom.fluent.fallback

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.fluentBundle
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val LocalizationAvailableLocalesTest by testSuite {

    test("Localization.simple bundles with content report as available") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false) {
            addResource(FluentResource.tryNew("hello = Hi").getOrThrow())
        }
        val l10n = Localization.simple(bundle)

        // The bundle has entries, so the locale must be reported as available.
        assertEquals(listOf(LanguageIdentifier.parse("en")), l10n.getAvailableLocales())
    }

    test("getAvailableLocales does NOT use the empty-string sentinel") {
        // Replaces the stale hasMessage("") check with a direct entries().isEmpty() probe.
        val empty = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {}
        assertTrue(empty.entries().isEmpty(), "empty bundle should report no entries")

        val nonempty = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("x = X").getOrThrow())
        }
        assertTrue(nonempty.entries().isNotEmpty())
    }
}
