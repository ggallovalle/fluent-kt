package dev.kbroom.fluent.syntax

import dev.kbroom.fluent.syntax.parser.FluentParser
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import de.infix.testBalloon.framework.core.testSuite

val FluentSyntaxTest by testSuite {

    test("parse simple message") {
        val parser = FluentParser()
        val source = "hello = Hello, World!"
        val resource = parser.parse(source)
        assertEquals(1, resource.body.size)
        val message = resource.body[0] as Entry.Message
        assertEquals("hello", message.id.name)
        assertTrue(message.value != null)
    }

    test("parse multiple messages") {
        val parser = FluentParser()
        val source = "hello = Hello\ngoodbye = Goodbye\nwelcome = Welcome!"
        val resource = parser.parse(source)
        assertEquals(3, resource.body.size)
    }

    test("parse with comments") {
        val parser = FluentParser()
        val source = "## This is a comment\nhello = Hello, World!"
        val resource = parser.parse(source)
        assertEquals(2, resource.body.size)
        assertTrue(resource.body[0] is Entry.GroupComment)
    }

    test("parse runtime strips comments") {
        val parser = FluentParser()
        val source = "## This is a comment\nhello = Hello, World!"
        val resource = parser.parseRuntime(source)
        assertEquals(1, resource.body.size)
        assertTrue(resource.body[0] is Entry.Message)
    }
}
