package dev.kbroom.fluent.intl

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
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
data class FormatterKey(val name: String, val options: Any? = null)

/**
 * Per-language memoizer that caches instances by type.
 *
 * Thread-safe via copy-on-write [AtomicRef] maps. Concurrent readers never
 * block; writers contend via CAS, with the loser rebuilding from the winner's
 * snapshot. The factory passed to [withTryGet] / [getOrCreateFormatter] may
 * run more than once under heavy contention (a benign form of wasted work —
 * the cache only ever holds one canonical instance per key, and factories
 * are expected to be pure).
 *
 * Safe to share across threads after a
 * [dev.kbroom.fluent.bundle.FluentBundle] has been sealed via `seal()`.
 */
class IntlLangMemoizer {
    private val cache: AtomicRef<Map<KClass<*>, Any>> = atomic(emptyMap())
    private val formatterCache: AtomicRef<Map<FormatterKey, Any>> = atomic(emptyMap())

    /**
     * Try to get a cached instance of type T, or create one if not cached.
     *
     * @param factory Called only if the value is not already cached
     * @param block Called with the (potentially cached) instance
     * @return The result of [block]
     */
    fun <T : Any, R> withTryGet(clazz: KClass<T>, factory: () -> T, block: (T) -> R): R {
        @Suppress("UNCHECKED_CAST")
        val existing = cache.value[clazz] as? T
        if (existing != null) return block(existing)

        val created = factory()
        var winner: T = created
        cache.update { current ->
            @Suppress("UNCHECKED_CAST")
            val currentValue = current[clazz] as? T
            if (currentValue != null) {
                winner = currentValue
                current
            } else {
                current + (clazz to created)
            }
        }
        return block(winner)
    }

    /**
     * Get or create a formatter with the given name and options.
     * The options should be a data class with consistent equals/hashCode.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrCreateFormatter(name: String, options: Any?, factory: () -> T): T {
        val key = FormatterKey(name, options)
        val existing = formatterCache.value[key] as? T
        if (existing != null) return existing

        val created = factory()
        var winner: T = created
        formatterCache.update { current ->
            @Suppress("UNCHECKED_CAST")
            val currentValue = current[key] as? T
            if (currentValue != null) {
                winner = currentValue
                current
            } else {
                current + (key to created)
            }
        }
        return winner
    }

    /**
     * Get a cached value if it exists.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(clazz: KClass<T>): T? = cache.value[clazz] as? T

    /**
     * Get a cached formatter if it exists.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getFormatterOrNull(name: String, options: Any?): T? =
        formatterCache.value[FormatterKey(name, options)] as? T

    /**
     * Clear all cached values for this language.
     */
    fun clear() {
        cache.value = emptyMap()
        formatterCache.value = emptyMap()
    }

    /**
     * Get the number of cached items.
     */
    fun size(): Int = cache.value.size
}

/**
 * Multi-locale memoizer container.
 *
 * Maintains separate memoizers for each language, allowing efficient
 * reuse of expensive-to-create locale-specific objects. Thread-safe via
 * copy-on-write (same scheme as [IntlLangMemoizer]).
 */
class IntlMemoizer {
    private val memoizers: AtomicRef<Map<LanguageIdentifier, IntlLangMemoizer>> = atomic(emptyMap())

    /**
     * Get or create a memoizer for the given language.
     */
    fun getForLang(langId: LanguageIdentifier): IntlLangMemoizer {
        val existing = memoizers.value[langId]
        if (existing != null) return existing

        val created = IntlLangMemoizer()
        var winner: IntlLangMemoizer = created
        memoizers.update { current ->
            val currentValue = current[langId]
            if (currentValue != null) {
                winner = currentValue
                current
            } else {
                current + (langId to created)
            }
        }
        return winner
    }

    /**
     * Clear all memoizers.
     */
    fun clear() {
        memoizers.value = emptyMap()
    }

    /**
     * Get the number of memoizers.
     */
    fun size(): Int = memoizers.value.size
}
