package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val SetUseIsolatingTest by testSuite {

    test("useIsolating defaults to true and emits bidi isolation marks") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = before { \$x } after")
        }
        val isolated = bundle.format(
            "msg",
            FluentArgs().apply { set("x", "VAL") },
        )
        assertTrue(isolated!!.length > "before VAL after".length, "expected isolation marks; got $isolated")
    }

    test("useIsolating=false via builder DSL produces unisolated output") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false) {
            resource("msg = before { \$x } after")
        }
        val plain = bundle.format(
            "msg",
            FluentArgs().apply { set("x", "VAL") },
        )
        assertEquals("before VAL after", plain)
    }
}
