package dev.kbroom.fluent.bundle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.bundle.types.FluentNumber
import dev.kbroom.fluent.bundle.types.fluentValueOf

/**
 * Tests for FluentValue types and conversions
 */
class TypesTest {
    
    @Test
    fun testFluentValueString() {
        val value = FluentValue.Str("hello")
        assertEquals("hello", value.asString())
    }
    
    @Test
    fun testFluentValueNumber() {
        val value = FluentValue.Number(FluentNumber(42.0))
        assertEquals("42", value.asString())
    }
    
    @Test
    fun testFluentValueNumberDecimal() {
        val value = FluentValue.Number(FluentNumber(3.14))
        assertTrue(value.asString().startsWith("3.14"))
    }
    
    @Test
    fun testFluentValueNone() {
        val value = FluentValue.None
        assertEquals("", value.asString())
    }
    
    @Test
    fun testFluentNumberCreation() {
        val num = FluentNumber(42.0)
        assertEquals(42.0, num.value)
    }
    
    @Test
    fun testFluentArgsSet() {
        val args = FluentArgs()
        args.set("name", "World")
        
        assertTrue(args.contains("name"))
    }
    
    @Test
    fun testFluentArgsGet() {
        val args = FluentArgs()
        args.set("name", "World")
        
        val value = args.get("name")
        assertTrue(value is FluentValue.Str)
    }
    
    @Test
    fun testValueFromNumber() {
        val value = fluentValueOf(42)
        assertTrue(value is FluentValue.Number)
    }
    
    @Test
    fun testValueFromString() {
        val value = fluentValueOf("test")
        assertTrue(value is FluentValue.Str)
    }
    
    @Test
    fun testValueFromDouble() {
        val value = fluentValueOf(3.14)
        assertTrue(value is FluentValue.Number)
    }
    
    @Test
    fun testOptionalValuePresent() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("hello = Hello World").getOrThrow())
        
        val message = bundle.getMessage("hello")
        assertTrue(message != null)
    }
}
