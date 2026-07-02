package dev.kbroom.fluent.syntax

import dev.kbroom.fluent.syntax.parser.FluentParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FluentSyntaxTest {
    
    @Test
    fun testParseSimpleMessage() {
        val parser = FluentParser()
        val source = "hello = Hello, World!"
        val resource = parser.parse(source)
        assertEquals(1, resource.body.size)
        val message = resource.body[0] as Entry.Message
        assertEquals("hello", message.id.name)
        assertTrue(message.value != null)
    }
    
    @Test
    fun testParseMultipleMessages() {
        val parser = FluentParser()
        val source = "hello = Hello\ngoodbye = Goodbye\nwelcome = Welcome!"
        val resource = parser.parse(source)
        assertEquals(3, resource.body.size)
    }
    
    @Test
    fun testParseWithComments() {
        val parser = FluentParser()
        val source = "## This is a comment\nhello = Hello, World!"
        val resource = parser.parse(source)
        assertEquals(2, resource.body.size)
        assertTrue(resource.body[0] is Entry.GroupComment)
    }
    
    @Test
    fun testParseRuntimeStripsComments() {
        val parser = FluentParser()
        val source = "## This is a comment\nhello = Hello, World!"
        val resource = parser.parseRuntime(source)
        assertEquals(1, resource.body.size)
        assertTrue(resource.body[0] is Entry.Message)
    }
}
