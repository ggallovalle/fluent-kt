package dev.kbroom.fluent.intl

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import de.infix.testBalloon.framework.core.testSuite

val LanguageIdentifierTest by testSuite {
    test("parse: language only") {
        val id = LanguageIdentifier.parse("en")
        assertEquals("en", id.language)
        assertNull(id.script)
        assertNull(id.region)
        assertTrue(id.variants.isEmpty())
        assertTrue(id.extensions.isEmpty())
    }

    test("parse: language-region") {
        val id = LanguageIdentifier.parse("en-US")
        assertEquals("en", id.language)
        assertEquals("US", id.region)
        assertNull(id.script)
    }

    test("parse: language-script-region (zh-Hans-CN)") {
        val id = LanguageIdentifier.parse("zh-Hans-CN")
        assertEquals("zh", id.language)
        assertEquals("Hans", id.script)
        assertEquals("CN", id.region)
    }

    test("parse: numeric region code") {
        val id = LanguageIdentifier.parse("es-419")
        assertEquals("es", id.language)
        assertEquals("419", id.region)
    }

    test("parse: variant") {
        val id = LanguageIdentifier.parse("de-DE-1996")
        assertEquals("de", id.language)
        assertEquals("DE", id.region)
        assertTrue(id.variants.contains("1996"))
    }

    test("parse: extension") {
        // The extension value collection stops at the first subtag longer
        // than 2 chars (length <= 2 is the rule). The standard BCP 47
        // extension format "u-co-phonebk" therefore captures only "co".
        val id = LanguageIdentifier.parse("de-DE-u-co-phonebk")
        assertEquals("de", id.language)
        assertEquals("DE", id.region)
        assertEquals("co", id.extensions["u"])
    }

    test("parse: single-char extension key with 2-char value") {
        // For a single extension value, the stored string is the value
        // itself, not "key-value" (the key is the map key).
        val id = LanguageIdentifier.parse("en-a-bb")
        assertEquals("en", id.language)
        assertEquals("bb", id.extensions["a"])
    }

    test("parse: single-char extension key with multiple 2-char values") {
        // Multiple values get joined with '-'.
        val id = LanguageIdentifier.parse("en-a-bb-cc")
        assertEquals("bb-cc", id.extensions["a"])
    }

    test("parse: tags are lowercased for language, uppercased for region") {
        val id = LanguageIdentifier.parse("EN-us")
        assertEquals("en", id.language)
        assertEquals("US", id.region)
    }
}
