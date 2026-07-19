package dev.kbroom.fluent.syntax

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.syntax.parser.FluentParser
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Broken-message parser behavior tests.
 *
 * Upstream fluent-rs treats messages that have no useful value (no value AND no
 * attributes, or every attribute value is empty) as `Junk`. The current parser
 * produces a `Message` in those cases, which silently corrupts the bundle.
 * Once the parser is fixed, the bundle-side `isBrokenMessage` workaround can be
 * removed.
 */
val ParseJunkMessagesTest by testSuite {

    test("err1: empty message body produces Junk instead of Message") {
        val parser = FluentParser()
        val source = "err1 =\n"
        val resource = parser.parse(source)
        assertEquals(1, resource.body.size, "expected exactly one body entry")
        assertTrue(
            resource.body[0] is Entry.Junk,
            "expected Junk entry, got ${resource.body[0]::class.simpleName}",
        )
    }

    test("err2: empty placeable {} produces Junk instead of Message with empty value") {
        val parser = FluentParser()
        val source = "err2 = {}\n"
        val resource = parser.parse(source)
        assertEquals(1, resource.body.size)
        assertTrue(
            resource.body[0] is Entry.Junk,
            "expected Junk entry, got ${resource.body[0]::class.simpleName}",
        )
    }

    test("err3: message with only an empty attribute produces Junk") {
        val parser = FluentParser()
        val source = "err3 =\n    .attr =\n"
        val resource = parser.parse(source)
        assertEquals(1, resource.body.size)
        assertTrue(
            resource.body[0] is Entry.Junk,
            "expected Junk entry, got ${resource.body[0]::class.simpleName}",
        )
    }

    test("err4: message with one valid attribute remains a Message") {
        val parser = FluentParser()
        val source = "err4 =\n    .attr1 = Attr\n    .attr2 =\n"
        val resource = parser.parse(source)
        assertEquals(1, resource.body.size)
        val msg = resource.body[0]
        assertTrue(msg is Entry.Message, "expected Message, got ${msg::class.simpleName}")
        assertEquals("err4", msg.id.name)
    }

    test("err1+err2+err3+err4 combined: each broken entry is Junk, the valid one is a Message") {
        val parser = FluentParser()
        val source = """
            |err1 =
            |err2 = {}
            |err3 =
            |    .attr =
            |err4 =
            |    .attr1 = Attr
            |    .attr2 =
        """.trimMargin()
        val resource = parser.parse(source)
        // 4 entries: 3 Junk + 1 Message
        assertEquals(4, resource.body.size)
        assertTrue(resource.body[0] is Entry.Junk, "err1 should be Junk")
        assertTrue(resource.body[1] is Entry.Junk, "err2 should be Junk")
        assertTrue(resource.body[2] is Entry.Junk, "err3 should be Junk")
        val msg = resource.body[3]
        assertTrue(msg is Entry.Message, "err4 should be Message")
        assertEquals("err4", msg.id.name)
    }

    test("valid simple message still parses as Message") {
        val parser = FluentParser()
        val source = "hello = Hello, world\n"
        val resource = parser.parse(source)
        assertEquals(1, resource.body.size)
        assertTrue(resource.body[0] is Entry.Message)
    }
}
