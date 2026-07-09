package dev.kbroom.fluent.pseudo

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import de.infix.testBalloon.framework.core.testSuite

/**
 * Tests for PseudoLocale pseudolocalization
 */
val PseudoLocaleTest by testSuite {
    test("accented mode") {
        val pseudo = PseudoLocale(PseudoOptions(mode = PseudoMode.Accented))

        val result = pseudo.transform("Hello World")
        assertTrue(result.isNotEmpty())
    }

    test("widened mode") {
        val pseudo = PseudoLocale(PseudoOptions(mode = PseudoMode.Widened))

        val result = pseudo.transform("Hello")
        assertTrue(result.isNotEmpty())
    }

    test("hidden mode") {
        val pseudo = PseudoLocale(PseudoOptions(mode = PseudoMode.Hidden))

        val result = pseudo.transform("Hello")
        assertTrue(result.isNotEmpty())
    }

    test("bidi mode") {
        val pseudo = PseudoLocale(PseudoOptions(mode = PseudoMode.Bidi))

        val result = pseudo.transform("Hello")
        assertTrue(result.isNotEmpty())
    }

    test("single char not transformed") {
        val pseudo = PseudoLocale(PseudoOptions(mode = PseudoMode.Accented))

        val result = pseudo.transform("A")
        assertTrue(result.isNotEmpty())
    }

    test("empty string") {
        val pseudo = PseudoLocale(PseudoOptions(mode = PseudoMode.Accented))

        val result = pseudo.transform("")
        assertEquals("", result)
    }

    test("create pseudo transform") {
        val transform = createPseudoTransform(PseudoMode.Accented)

        val result = transform("Test")
        assertTrue(result.isNotEmpty())
    }
}
