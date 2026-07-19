package dev.kbroom.fluent.syntax

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.syntax.parser.FluentParser
import dev.kbroom.fluent.syntax.serializer.serialize
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for AST to FTL serialization
 */
val SerializerTest by testSuite {

    test("serialize simple message") {
        val ftl = "hello = Hello World"
        val resource = FluentParser().parse(ftl)
        val serialized = serialize(resource)

        assertEquals("hello = Hello World", serialized.trim())
    }

    test("serialize term") {
        val ftl = "-brand = Firefox"

        val resource = FluentParser().parse(ftl)
        val serialized = serialize(resource)

        assertEquals("-brand = Firefox", serialized.trim())
    }

    test("serialize multiple messages") {
        val ftl = "hello = Hello\nworld = World"

        val resource = FluentParser().parse(ftl)
        val serialized = serialize(resource)

        assertTrue(serialized.contains("hello = Hello"))
        assertTrue(serialized.contains("world = World"))
    }

    test("round trip parse serialize") {
        val original = "hello = Hello"
        val resource1 = FluentParser().parse(original)
        val serialized = serialize(resource1)
        val resource2 = FluentParser().parse(serialized)

        assertEquals(resource1.body.size, resource2.body.size)
    }
}
