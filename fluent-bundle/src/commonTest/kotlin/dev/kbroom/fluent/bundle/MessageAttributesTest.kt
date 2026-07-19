package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

val MessageAttributesTest by testSuite {

    test("attributesMap returns attribute names mapped to raw Pattern") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource(
                """
                msg = Default value
                    .title = The title
                    .subtitle = The subtitle
                """.trimIndent(),
            )
        }

        val message = bundle.getMessage("msg")!!
        val map = message.attributesMap()
        assertEquals(2, map.size)
        assertEquals(setOf("title", "subtitle"), map.keys)
        assertNotNull(map["title"])
        assertNotNull(map["subtitle"])
    }

    test("attributesMap returns empty map when message has no attributes") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = Just a value")
        }

        val message = bundle.getMessage("msg")!!
        assertEquals(emptyMap(), message.attributesMap())
    }

    test("attributesMap preserves insertion order matching the AST") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource(
                """
                msg = default
                    .a = A
                    .c = C
                    .b = B
                """.trimIndent(),
            )
        }

        val message = bundle.getMessage("msg")!!
        assertEquals(listOf("a", "c", "b"), message.attributesMap().keys.toList())
    }

    test("existing attributes() still returns List<Attribute> AST view") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = v\n  .a = A")
        }
        val attrs = bundle.getMessage("msg")!!.attributes()
        assertEquals(1, attrs.size)
        assertEquals("a", attrs[0].id.name)
    }

    test("attribute() with missing name returns null") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("msg = v")
        }
        assertNull(bundle.getMessage("msg")!!.attributesMap()["nope"])
    }
}
