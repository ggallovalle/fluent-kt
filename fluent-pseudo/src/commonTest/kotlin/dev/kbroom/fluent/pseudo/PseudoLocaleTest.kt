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

    test("accented skips placeable contents") {
        val out = PseudoLocale.accented().transform("Hello { \$name } World")
        // Brace characters and placeable content preserved verbatim; outer
        // text transformed.
        assertTrue(out.startsWith("Ĥéĺĺó"), "outer text should be accented, got: $out")
        assertTrue(out.contains("{ \$name }"), "placeable content should be preserved, got: $out")
    }

    test("accented preserves ampersand-prefixed HTML entity") {
        val out = PseudoLocale.accented().transform("foo &amp; bar")
        // The & is preserved; "amp;" is treated as plain text and accented.
        assertTrue(out.startsWith("ƒóó &"), "got: $out")
    }

    test("widened transforms letters and preserves non-letters") {
        val out = PseudoLocale.widened().transform("abc 123")
        // Each letter maps to a single precomposed Unicode char with a
        // diacritic, so the length is unchanged from "abc 123".
        assertEquals(7, out.length)
        assertTrue(out.contains("123"), "digits preserved, got: $out")
        assertTrue(out.contains(" "), "space preserved, got: $out")
        // The first three characters are the diacritic forms, not "abc".
        assertTrue(out[0] != 'a' && out[1] != 'b' && out[2] != 'c')
    }

    test("hidden wraps letters in [x] form") {
        val out = PseudoLocale.hidden().transform("Hi!")
        assertEquals("[H][i]!", out)
    }

    test("bidi wraps input in RTL marks") {
        val out = PseudoLocale.bidi().transform("hello")
        assertEquals("\u202Bhello\u202C", out)
    }
}
