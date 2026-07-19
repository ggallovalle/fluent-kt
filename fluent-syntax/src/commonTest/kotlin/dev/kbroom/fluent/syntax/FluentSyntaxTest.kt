package dev.kbroom.fluent.syntax

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.syntax.parser.FluentParser
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val source = """
            |hello = Hello
            |goodbye = Goodbye
            |welcome = Welcome!
        """.trimMargin()
        val resource = parser.parse(source)
        assertEquals(3, resource.body.size)
    }

    test("parse with comments - binds as docComment") {
        val parser = FluentParser()
        val source = """
            |## This is a comment
            |hello = Hello, World!
        """.trimMargin()
        val resource = parser.parse(source)
        // With docComment binding, ## comment becomes part of the message
        assertEquals(1, resource.body.size)
        val message = resource.body[0] as Entry.Message
        assertEquals("hello", message.id.name)
        assertEquals("This is a comment", message.docComment?.description)
    }

    test("parse runtime strips comments") {
        val parser = FluentParser()
        val source = """
            |## This is a comment
            |hello = Hello, World!
        """.trimMargin()
        val resource = parser.parseRuntime(source)
        assertEquals(1, resource.body.size)
        assertTrue(resource.body[0] is Entry.Message)
    }
    test("parse doc comment with variable (colon separator)") {
        val parser = FluentParser()
        val source = $$"""
            |# Variables:
            |#   $x (String): desc
            |msg = { $x }
        """.trimMargin()
        val resource = parser.parse(source)
        val message = resource.body.firstOrNull() as? Entry.Message
        assertTrue(message != null)
        assertEquals(1, message.docComment?.variables?.size)
        val v = message.docComment?.variables?.get(0)
        assertEquals("x", v?.name)
        assertEquals("String", v?.type)
        assertEquals("desc", v?.description)
    }

    test("parse doc comment with variable (dash separator)") {
        val parser = FluentParser()
        val source = $$"""
            |# Variables:
            |#   $count (Number) - tab count
            |msg = { $count }
        """.trimMargin()
        val resource = parser.parse(source)
        val message = resource.body.firstOrNull() as? Entry.Message
        assertTrue(message != null)
        val v = message.docComment?.variables?.get(0)
        assertEquals("count", v?.name)
        assertEquals("Number", v?.type)
        assertEquals("tab count", v?.description)
    }

    test("parse doc comment with curly brace type and default") {
        val parser = FluentParser()
        val source = $$"""
            |# Variables:
            |#   $name {string, "Arial"} - font
            |msg = { $name }
        """.trimMargin()
        val resource = parser.parse(source)
        val message = resource.body.firstOrNull() as? Entry.Message
        assertTrue(message != null)
        val v = message.docComment?.variables?.get(0)
        assertEquals("name", v?.name)
        assertEquals("string", v?.type)
        assertEquals("Arial", v?.defaultValue)
        assertEquals("font", v?.description)
    }

    test("parse doc comment with no type") {
        val parser = FluentParser()
        val source = $$"""
            |# Variables:
            |#   $num - default value
            |msg = { $num }
        """.trimMargin()
        val resource = parser.parse(source)
        val message = resource.body.firstOrNull() as? Entry.Message
        assertTrue(message != null)
        val v = message.docComment?.variables?.get(0)
        assertEquals("num", v?.name)
        assertEquals("", v?.type)
        assertEquals("default value", v?.description)
    }

    test("parse doc comment with variable continuation line") {
        val parser = FluentParser()
        val source = $$"""
            |# Variables:
            |#   $x (String): first line
            |#   continuation
            |msg = { $x }
        """.trimMargin()
        val resource = parser.parse(source)
        val message = resource.body.firstOrNull() as? Entry.Message
        assertTrue(message != null)
        val v = message.docComment?.variables?.get(0)
        assertEquals("first line continuation", v?.description)
    }

    test("blank line between comment and message - comment binds anyway") {
        val parser = FluentParser()
        val source = """
            |# Doc
            |
            |greeting = Hello
        """.trimMargin()
        val resource = parser.parse(source)
        // Current implementation: comment binds regardless of blank line
        assertEquals(1, resource.body.size)
        val message = resource.body[0] as Entry.Message
        assertEquals("Doc", message.docComment?.description)
    }

    test("double hash binds as docComment") {
        val parser = FluentParser()
        val source = $$"""
            |## Variables:
            |##   $x (String): desc
            |msg = { $x }
        """.trimMargin()
        val resource = parser.parse(source)
        assertEquals(1, resource.body.size)
        val message = resource.body[0] as Entry.Message
        assertEquals(1, message.docComment?.variables?.size)
    }

    test("triple hash never binds - standalone") {
        val parser = FluentParser()
        val source = """
            |### Resource comment
            |msg = Hello
        """.trimMargin()
        val resource = parser.parse(source)
        // ### comments should never bind
        assertEquals(2, resource.body.size)
        assertTrue(resource.body[0] is Entry.ResourceComment)
        val message = resource.body[1] as Entry.Message
        assertEquals(null, message.docComment)
    }
}
