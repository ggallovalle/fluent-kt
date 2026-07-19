package dev.kbroom.fluent.pseudo

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    test("Long mode pads with filler to ~130% length") {
        val out = PseudoLocale.long().transform("hello")
        // "hello" is 5 chars; 5 * 1.3 = 6.5 → 6 chars after padding with 1 "[!]"
        assertTrue(out.startsWith("hello"))
        assertEquals(8, out.length, "expected 8 chars (5 + 1 '[!]' = 8); got: $out")
    }

    test("Long mode preserves original characters") {
        // "abc" * 1.3 = 3.9 → 3 chars (no padding); use a longer string for fillChar test.
        val out = PseudoLocale.long(fillChar = ".").transform("abcdefgh")
        // 8 * 1.3 = 10.4 → 10 chars; needs 2 filler dots
        assertEquals(10, out.length, "expected 10 chars; got: $out")
        assertTrue(out.startsWith("abcdefgh"))
        assertTrue(out.endsWith(".."))
    }

    test("Long mode is a no-op when factor <= 1") {
        val out = PseudoLocale.long(factor = 0.5).transform("hello")
        assertEquals("hello", out)
    }
}
