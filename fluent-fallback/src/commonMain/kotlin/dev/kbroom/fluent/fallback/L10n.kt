package dev.kbroom.fluent.fallback

import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentArgs
import dev.kbroom.fluent.intl.LanguageIdentifier

/**
 * Localization message wrapper.
 */
data class L10nMessage(
    val id: String,
    val value: String?,
    val attributes: Map<String, String> = emptyMap()
)

/**
 * Localization key.
 */
data class L10nKey(
    val id: String,
    val attribute: String? = null
)

/**
 * Localization attribute.
 */
data class L10nAttribute(
    val key: L10nKey,
    val value: String
)

/**
 * Bundle generator interface.
 */
interface BundleGenerator {
    fun generateBundles(locales: List<LanguageIdentifier>, resourceIds: List<ResourceId>): BundleIterator
}

/**
 * Bundle iterator.
 */
interface BundleIterator {
    fun next(): FluentBundle?
}

/**
 * Localization manager.
 */
class Localization<G, P>(
    private val generator: G
) where G : BundleGenerator, P : BundleIterator {
    
    private var currentLocales: List<LanguageIdentifier> = emptyList()
    private var currentBundles: List<FluentBundle> = emptyList()
    
    /**
     * Set locales for localization.
     */
    fun setLocales(locales: List<LanguageIdentifier>) {
        currentLocales = locales
    }
    
    /**
     * Get current locales.
     */
    fun getLocales(): List<LanguageIdentifier> = currentLocales
    
    /**
     * Format a message.
     */
    fun format(id: String, args: FluentArgs? = null): String? {
        for (bundle in currentBundles) {
            val result = bundle.format(id, args)
            if (result != null) return result
        }
        return null
    }
    
    /**
     * Check if a message exists.
     */
    fun has(id: String): Boolean {
        return currentBundles.any { it.hasMessage(id) }
    }
}
