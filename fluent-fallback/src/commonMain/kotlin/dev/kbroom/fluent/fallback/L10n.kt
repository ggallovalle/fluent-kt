package dev.kbroom.fluent.fallback

import dev.kbroom.fluent.bundle.FluentArgs
import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentError
import dev.kbroom.fluent.intl.LanguageIdentifier

/**
 * Localization message wrapper.
 */
data class L10nMessage(val id: String, val value: String?, val attributes: Map<String, String> = emptyMap())

/**
 * Localization key.
 */
data class L10nKey(val id: String, val attribute: String? = null)

/**
 * Localization attribute.
 */
data class L10nAttribute(val key: L10nKey, val value: String)

/**
 * Errors from localization operations.
 */
sealed class L10nError {
    data class MissingBundle(val locale: LanguageIdentifier, val resourceId: ResourceId) : L10nError()
    data class MissingResource(val resourceId: ResourceId, val locales: List<LanguageIdentifier>) : L10nError()
    data class ResolverError(val errors: List<FluentError>) : L10nError()
}

/**
 * Bundle generator interface.
 */
interface BundleGenerator {
    fun generateBundles(locales: List<LanguageIdentifier>, resourceIds: List<ResourceId>): BundleIterator

    /**
     * Generate bundles asynchronously.
     */
    suspend fun generateBundlesAsync(
        locales: List<LanguageIdentifier>,
        resourceIds: List<ResourceId>,
    ): AsyncBundleIterator
}

/**
 * Bundle iterator.
 */
interface BundleIterator {
    fun next(): FluentBundle?

    /**
     * Get all bundles.
     */
    fun toList(): List<FluentBundle> = generateSequence { next() }.toList()
}

/**
 * Async bundle iterator.
 */
interface AsyncBundleIterator {
    suspend fun next(): FluentBundle?

    suspend fun toList(): List<FluentBundle> {
        val bundles = mutableListOf<FluentBundle>()
        while (true) {
            val bundle = next() ?: break
            bundles.add(bundle)
        }
        return bundles
    }
}

/**
 * Locale change listener.
 */
interface LocaleChangeListener {
    fun onLocalesChanged(newLocales: List<LanguageIdentifier>, oldLocales: List<LanguageIdentifier>)
}

/**
 * Localization manager.
 */
open class Localization<G, P, A>(
    protected val generator: G,
) where G : BundleGenerator, P : BundleIterator, A : AsyncBundleIterator {

    private var currentLocales: List<LanguageIdentifier> = emptyList()
    private var currentBundles: List<FluentBundle> = emptyList()
    private var resourceIds: List<ResourceId> = emptyList()
    private val localeListeners: MutableList<LocaleChangeListener> = mutableListOf()
    private var currentErrors: List<L10nError> = emptyList()

    /**
     * Set locales for localization.
     */
    fun setLocales(locales: List<LanguageIdentifier>) {
        val oldLocales = currentLocales
        currentLocales = locales
        regenerateBundles()

        // Notify listeners
        if (oldLocales != locales) {
            localeListeners.forEach { it.onLocalesChanged(locales, oldLocales) }
        }
    }

    /**
     * Set resource IDs to load.
     */
    fun setResourceIds(ids: List<ResourceId>) {
        resourceIds = ids
        regenerateBundles()
    }

    /**
     * Get current locales.
     */
    fun getLocales(): List<LanguageIdentifier> = currentLocales

    /**
     * Get current bundles.
     */
    fun getBundles(): List<FluentBundle> = currentBundles

    /**
     * Get errors from the last operation.
     */
    fun getErrors(): List<L10nError> = currentErrors

    /**
     * Format a message.
     */
    fun format(id: String, args: FluentArgs? = null): String? {
        currentErrors = emptyList()

        for ((index, bundle) in currentBundles.withIndex()) {
            val result = bundle.format(id, args)
            if (result != null) return result

            // Check if this is because the resource is missing (for optional resources)
            if (index < resourceIds.size) {
                val resourceId = resourceIds[index]
                if (resourceId.type == ResourceType.Required && !bundle.hasMessage(id)) {
                    // Required resource - log error
                    currentErrors = currentErrors + L10nError.MissingBundle(
                        currentLocales.getOrElse(index) { currentLocales.first() },
                        resourceId,
                    )
                }
            }
        }
        return null
    }

    /**
     * Format a message synchronously with full control.
     */
    fun formatWithErrors(id: String, args: FluentArgs? = null): Pair<String?, List<FluentError>> {
        val allErrors = mutableListOf<FluentError>()

        for (bundle in currentBundles) {
            val errors = mutableListOf<FluentError>()
            val result = bundle.formatMessage(id, args)
            if (result != null) {
                return Pair(result, allErrors + errors)
            }
            allErrors.addAll(errors)
        }
        return Pair(null, allErrors)
    }

    /**
     * Format a value (message without attributes).
     */
    fun formatValue(id: String, args: FluentArgs? = null): String? = format(id, args)

    /**
     * Format an attribute.
     */
    fun formatAttribute(id: String, attribute: String, args: FluentArgs? = null): String? {
        for (bundle in currentBundles) {
            val message = bundle.getMessage(id) ?: continue
            // Would need to add attribute formatting to FluentMessage
            val result = bundle.format("$id.$attribute", args)
            if (result != null) return result
        }
        return null
    }

    /**
     * Check if a message exists.
     */
    fun has(id: String): Boolean = currentBundles.any { it.hasMessage(id) }

    /**
     * Check if a specific locale has a message.
     */
    fun hasInLocale(id: String, locale: LanguageIdentifier): Boolean {
        val index = currentLocales.indexOf(locale)
        if (index >= 0 && index < currentBundles.size) {
            return currentBundles[index].hasMessage(id)
        }
        return false
    }

    /**
     * Get all available locales.
     */
    fun getAvailableLocales(): List<LanguageIdentifier> = currentBundles.filter {
        it.hasMessage("")
    }.mapIndexedNotNull { index, _ ->
        currentLocales.getOrNull(index)
    }

    /**
     * Add a locale change listener.
     */
    fun addLocaleChangeListener(listener: LocaleChangeListener) {
        localeListeners.add(listener)
    }

    /**
     * Remove a locale change listener.
     */
    fun removeLocaleChangeListener(listener: LocaleChangeListener) {
        localeListeners.remove(listener)
    }

    /**
     * Force regeneration of bundles.
     */
    fun regenerateBundles() {
        if (currentLocales.isEmpty() || resourceIds.isEmpty()) {
            currentBundles = emptyList()
            return
        }

        val iterator = generator.generateBundles(currentLocales, resourceIds)
        currentBundles = iterator.toList()

        // Check for missing required resources
        currentErrors = emptyList()
        for ((index, resourceId) in resourceIds.withIndex()) {
            if (resourceId.type == ResourceType.Required) {
                val locale = currentLocales.getOrElse(index) { currentLocales.first() }
                if (index >= currentBundles.size || currentBundles.getOrNull(index)?.hasMessage("") != true) {
                    currentErrors = currentErrors + L10nError.MissingResource(resourceId, currentLocales)
                }
            }
        }
    }

    /**
     * Create a simple localization with a single bundle.
     */
    companion object {
        fun simple(bundle: FluentBundle): Localization<*, *, *> = SimpleLocalization(bundle)
    }
}

/**
 * Simple localization for single bundle use case.
 */
class SimpleLocalization(private val bundle: FluentBundle) :
    Localization<SimpleGenerator, SimpleIterator, SimpleAsyncIterator>(SimpleGenerator(bundle)) {

    init {
        setLocales(bundle.locales)
    }
}

class SimpleGenerator(private val bundle: FluentBundle) : BundleGenerator {
    override fun generateBundles(locales: List<LanguageIdentifier>, resourceIds: List<ResourceId>): BundleIterator =
        SimpleIterator(listOf(bundle))

    override suspend fun generateBundlesAsync(
        locales: List<LanguageIdentifier>,
        resourceIds: List<ResourceId>,
    ): AsyncBundleIterator = SimpleAsyncIterator(listOf(bundle))
}

class SimpleIterator(private val bundles: List<FluentBundle>) : BundleIterator {
    private var index = 0
    override fun next(): FluentBundle? = if (index < bundles.size) bundles[index++] else null
}

class SimpleAsyncIterator(private val bundles: List<FluentBundle>) : AsyncBundleIterator {
    private var index = 0
    override suspend fun next(): FluentBundle? = if (index < bundles.size) bundles[index++] else null
}
