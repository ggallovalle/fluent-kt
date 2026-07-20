package dev.kbroom.fluent.compose

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.kbroom.fluent.fallback.ResourceId
import dev.kbroom.fluent.intl.LanguageIdentifier
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Robolectric unit tests for asset loading.
 *
 * Uses JUnit4 + RobolectricTestRunner (not testBalloon) because
 * [ApplicationProvider] requires the Robolectric instrumentation context.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AssetResourceManagerTest {
    @Test
    fun loadsFtlFromAssetsForLocale() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = AssetResourceManager(context.assets, basePath = "i18n")
        val bundle = manager.loadBundle(
            LanguageIdentifier.parse("en-US"),
            listOf(ResourceId("app/messages")),
        )
        assertEquals("Hello", bundle.formatMessage("hello"))
    }

    @Test
    fun loadsEsMxRegistryEntry() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = AssetResourceManager(context.assets, basePath = "i18n")
        val registry = manager.loadRegistry(
            locale = LanguageIdentifier.parse("es-MX"),
            resourceIdsByBundle = mapOf("app" to listOf(ResourceId("app/messages"))),
            fallbackLocale = LanguageIdentifier.parse("en-US"),
        )
        assertTrue(registry.contains("app"))
        assertEquals("Hola", registry.get("app").formatMessage("hello"))
    }

    @Test
    fun fallsBackToEnUsWhenLocaleMissing() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = AssetResourceManager(context.assets, basePath = "i18n")
        val registry = manager.loadRegistry(
            locale = LanguageIdentifier.parse("fr-FR"),
            resourceIdsByBundle = mapOf("app" to listOf(ResourceId("app/messages"))),
            fallbackLocale = LanguageIdentifier.parse("en-US"),
        )
        assertEquals("Hello", registry.get("app").formatMessage("hello"))
    }
}
