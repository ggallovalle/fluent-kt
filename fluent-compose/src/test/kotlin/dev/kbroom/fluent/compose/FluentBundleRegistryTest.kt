package dev.kbroom.fluent.compose

import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.fluentBundle
import dev.kbroom.fluent.intl.LanguageIdentifier
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FluentBundleRegistryTest {
    @Test
    fun getReturnsRegisteredBundle() {
        val resource = FluentResource.tryNew("hello = Hello").getOrThrow()
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResourceOverriding(resource)
        }
        val registry = FluentBundleRegistry(mapOf("app" to bundle))
        assertTrue(registry.contains("app"))
        assertEquals(setOf("app"), registry.names())
        assertEquals("Hello", registry.get("app").formatMessage("hello"))
    }

    @Test
    fun getThrowsForUnknownBundle() {
        val registry = FluentBundleRegistry(emptyMap())
        assertFalse(registry.contains("app"))
        assertFailsWith<IllegalStateException> { registry.get("app") }
    }
}
