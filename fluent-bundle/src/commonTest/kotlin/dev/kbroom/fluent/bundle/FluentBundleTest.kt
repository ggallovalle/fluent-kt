package dev.kbroom.fluent.bundle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.kbroom.fluent.intl.LanguageIdentifier

/**
 * Tests for FluentBundle operations
 */
class FluentBundleTest {
    
    @Test
    fun testCreateBundle() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        assertEquals(1, bundle.locales.size)
    }
    
    @Test
    fun testParseResource() {
        val resource = FluentResource.tryNew("hello = Hello!")
        assertTrue(resource.isSuccess)
    }
    
    @Test
    fun testAddResource() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val resource = FluentResource.tryNew("hello = Hello!").getOrThrow()
        val result = bundle.addResource(resource)
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun testFormatMessage() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val resource = FluentResource.tryNew("hello = Hello!").getOrThrow()
        bundle.addResource(resource)
        
        val result = bundle.format("hello", null)
        assertEquals("Hello!", result)
    }
    
    @Test
    fun testMessageReference() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("foo = Foo").getOrThrow())
        bundle.addResource(FluentResource.tryNew("ref = { foo }").getOrThrow())
        
        assertEquals("Foo", bundle.format("ref", null))
    }
    
    @Test
    fun testMissingMessage() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("ref = { missing }").getOrThrow())
        
        assertEquals("{missing}", bundle.format("ref", null))
    }
    
    @Test
    fun testResourceOverride() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val res1 = FluentResource.tryNew("key = Value").getOrThrow()
        val res2 = FluentResource.tryNew("key = Value 2").getOrThrow()
        
        bundle.addResource(res1)
        assertEquals("Value", bundle.format("key", null))
        
        bundle.addResourceOverriding(res2)
        assertEquals("Value 2", bundle.format("key", null))
    }
    
    @Test
    fun testTermReference() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("-brand = Firefox").getOrThrow())
        bundle.addResource(FluentResource.tryNew("app = Using { -brand }").getOrThrow())
        
        assertEquals("Using Firefox", bundle.format("app", null))
    }
}
