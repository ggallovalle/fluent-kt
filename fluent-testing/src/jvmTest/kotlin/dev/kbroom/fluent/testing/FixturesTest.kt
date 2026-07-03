package dev.kbroom.fluent.testing

import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.resmgr.ResourceManager
import dev.kbroom.fluent.fallback.ResourceId
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test that uses real Firefox FTL fixtures.
 */
class FixturesTest {
    
    private val fixturesPath = "src/jvmTest/resources/fixtures"
    
    private fun loadResource(path: String): FluentResource? {
        val file = File("$fixturesPath/$path.ftl")
        if (!file.exists()) return null
        return FluentResource.tryNew(file.readText()).getOrNull()
    }
    
    @Test
    fun testBrowserEnUs() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
        val resource = loadResource("en-US/browser/browser")
        assertTrue(resource != null, "Should load en-US/browser/browser.ftl")
        bundle.addResource(resource!!)
        bundle.addBuiltins()
        
        val result = bundle.format("browser-main-window-title", null)
        assertNotNull(result, "Should find browser-main-window-title")
    }
    
    @Test
    fun testBrowserPolish() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("pl")))
        val resource = loadResource("pl/browser/browser")
        assertTrue(resource != null, "Should load pl/browser/browser.ftl")
        bundle.addResource(resource!!)
        bundle.addBuiltins()
        
        val result = bundle.format("browser-main-window-title", null)
        assertNotNull(result, "Should find browser-main-window-title in Polish")
    }
    
    @Test
    fun testFallback() {
        val bundle = FluentBundle(listOf(
            LanguageIdentifier.parse("pl"),
            LanguageIdentifier.parse("en-US")
        ))
        
        loadResource("pl/browser/browser")?.let { bundle.addResource(it) }
        loadResource("en-US/browser/browser")?.let { bundle.addResource(it) }
        bundle.addBuiltins()
        
        val result = bundle.format("browser-main-window-title", null)
        assertNotNull(result, "Should find browser-main-window-title")
    }
    
    @Test
    fun testMultipleResources() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
        
        loadResource("en-US/browser/browser")?.let { bundle.addResource(it) }
        loadResource("en-US/browser/branding/brandings")?.let { bundle.addResource(it) }
        bundle.addBuiltins()
        
        assertTrue(bundle.hasMessage("browser-main-window-title"), "Should have browser-main-window-title")
    }
    
    @Test
    fun testResourceManager() {
        val rm = ResourceManager(fixturesPath)
        val bundle = rm.getBundle(
            listOf(LanguageIdentifier.parse("en-US")),
            listOf(ResourceId("browser/browser"))
        )
        
        val result = bundle.format("browser-main-window-title", null)
        assertNotNull(result, "Should find browser-main-window-title")
    }
}
