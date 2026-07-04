package dev.kbroom.fluent.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.kbroom.fluent.syntax.parser.FluentParser
import dev.kbroom.fluent.syntax.serialize

/**
 * Tests for AST to FTL serialization
 */
class SerializerTest {
    
    @Test
    fun testSerializeSimpleMessage() {
        val ftl = "hello = Hello World"
        val resource = FluentParser().parse(ftl)
        val serialized = serialize(resource)
        
        assertEquals("hello = Hello World", serialized.trim())
    }
    
    @Test
    fun testSerializeTerm() {
        val ftl = "-brand = Firefox"
        
        val resource = FluentParser().parse(ftl)
        val serialized = serialize(resource)
        
        assertEquals("-brand = Firefox", serialized.trim())
    }
    
    @Test
    fun testSerializeMultipleMessages() {
        val ftl = "hello = Hello\nworld = World"
        
        val resource = FluentParser().parse(ftl)
        val serialized = serialize(resource)
        
        assertTrue(serialized.contains("hello = Hello"))
        assertTrue(serialized.contains("world = World"))
    }
    
    @Test
    fun testRoundTripParseSerialize() {
        val original = "hello = Hello"
        val resource1 = FluentParser().parse(original)
        val serialized = serialize(resource1)
        val resource2 = FluentParser().parse(serialized)
        
        assertEquals(resource1.body.size, resource2.body.size)
    }
}
