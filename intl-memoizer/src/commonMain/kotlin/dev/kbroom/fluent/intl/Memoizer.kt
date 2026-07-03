package dev.kbroom.fluent.intl

import kotlin.reflect.KClass

/**
 * Interface for types that can provide a memoization key.
 * This replaces the Rust trait functionality.
 */
interface Memoizable {
    /**
     * Returns a key that uniquely identifies this memoized instance.
     */
    fun getMemoizerKey(): Any
}

/**
 * Key for memoizing formatters with specific options.
 */
data class FormatterKey(
    val name: String,
    val options: Any? = null
)

/**
 * Per-language memoizer that caches instances by type.
 * 
 * Maintains a map from KClass to cached instance for a single locale.
 * Also supports caching formatters by name + options.
 */
class IntlLangMemoizer {
    private val cache: MutableMap<KClass<*>, Any> = LinkedHashMap()
    private val formatterCache: MutableMap<FormatterKey, Any> = LinkedHashMap()
    
    /**
     * Try to get a cached instance of type T, or create one if not cached.
     * 
     * @param factory Called only if the value is not already cached
     * @param block Called with the (potentially cached) instance
     * @return The result of [block]
     */
    fun <T : Any, R> withTryGet(
        clazz: KClass<T>,
        factory: () -> T,
        block: (T) -> R
    ): R {
        @Suppress("UNCHECKED_CAST")
        val existing = cache[clazz] as? T
        return if (existing != null) {
            block(existing)
        } else {
            val created = factory()
            cache[clazz] = created
            block(created)
        }
    }
    
    /**
     * Get or create a formatter with the given name and options.
     * The options should be a data class with consistent equals/hashCode.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrCreateFormatter(
        name: String,
        options: Any?,
        factory: () -> T
    ): T {
        val key = FormatterKey(name, options)
        val existing = formatterCache[key] as? T
        return existing ?: run {
            val created = factory()
            formatterCache[key] = created
            created
        }
    }
    
    /**
     * Get a cached value if it exists.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(clazz: KClass<T>): T? = cache[clazz] as? T
    
    /**
     * Get a cached formatter if it exists.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getFormatterOrNull(name: String, options: Any?): T? {
        val key = FormatterKey(name, options)
        return formatterCache[key] as? T
    }
    
    /**
     * Clear all cached values for this language.
     */
    fun clear() {
        cache.clear()
        formatterCache.clear()
    }
    
    /**
     * Get the number of cached items.
     */
    fun size(): Int = cache.size
}

/**
 * Multi-locale memoizer container.
 * 
 * Maintains separate memoizers for each language, allowing efficient
 * reuse of expensive-to-create locale-specific objects.
 */
class IntlMemoizer {
    private val memoizers: MutableMap<LanguageIdentifier, IntlLangMemoizer> = LinkedHashMap()
    
    /**
     * Get or create a memoizer for the given language.
     */
    fun getForLang(langId: LanguageIdentifier): IntlLangMemoizer {
        return memoizers.getOrPut(langId) { IntlLangMemoizer() }
    }
    
    /**
     * Clear all memoizers.
     */
    fun clear() {
        memoizers.clear()
    }
    
    /**
     * Get the number of memoizers.
     */
    fun size(): Int = memoizers.size
}
