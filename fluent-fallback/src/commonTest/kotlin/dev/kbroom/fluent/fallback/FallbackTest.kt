package dev.kbroom.fluent.fallback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.intl.LanguageIdentifier

/**
 * Tests for fallback localization behavior
 */
class FallbackTest {
    
    @Test
    fun testBundleLocale() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
        assertEquals(1, bundle.locales.size)
    }
    
    @Test
    fun testBundleAddResource() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val resource = FluentResource.tryNew("hello = Hello").getOrThrow()
        val result = bundle.addResource(resource)
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun testSimpleLocalization() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("hello = Hello World").getOrThrow())
        
        val result = bundle.format("hello", null)
        assertEquals("Hello World", result)
    }
    
    @Test
    fun testMessageNotFound() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("hello = Hello").getOrThrow())
        
        val result = bundle.format("missing", null)
        assertEquals(null, result)
    }
    
    @Test
    fun testEmptyResource() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        val resource = FluentResource.tryNew("").getOrThrow()
        bundle.addResource(resource)
        
        assertTrue(!bundle.hasMessage("anything"))
    }
    
    @Test
    fun testMultipleResources() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("hello = Hello").getOrThrow())
        bundle.addResource(FluentResource.tryNew("world = World").getOrThrow())
        
        assertEquals("Hello", bundle.format("hello", null))
        assertEquals("World", bundle.format("world", null))
    }
}
