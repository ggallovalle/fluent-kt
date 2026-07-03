package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.syntax.Pattern
/**
 * Thread-safe FluentBundle wrapper for concurrent use.
 * Provides synchronized access to bundle formatting.
 */
class ConcurrentFluentBundle(
    private val delegate: FluentBundle
) {
    constructor(locales: List<LanguageIdentifier>, useIsolating: Boolean = true) :
        this(FluentBundle(locales, useIsolating))
    
    /**
     * Format a message thread-safely.
     */
    fun format(id: String, args: FluentArgs? = null): String? = synchronized(this) {
        delegate.format(id, args)
    }
    
    /**
     * Format pattern thread-safely.
     */
    fun formatPattern(
        pattern: Pattern,
        args: FluentArgs? = null,
        errors: MutableList<FluentError> = mutableListOf()
    ): String = synchronized(this) {
        delegate.formatPattern(pattern, args, errors)
    }
    
    /**
     * Add resource thread-safely.
     */
    fun addResource(resource: FluentResource): Result<Unit> = synchronized(this) {
        delegate.addResource(resource)
    }
    
    /**
     * Add resource with override thread-safely.
     */
    fun addResourceOverriding(resource: FluentResource): Result<Unit> = synchronized(this) {
        delegate.addResourceOverriding(resource)
    }
    
    /**
     * Get message thread-safely.
     */
    fun getMessage(id: String): FluentMessage? = synchronized(this) {
        delegate.getMessage(id)
    }
    
    /**
     * Check if message exists thread-safely.
     */
    fun hasMessage(id: String): Boolean = synchronized(this) {
        delegate.hasMessage(id)
    }
    
    /**
     * Add builtin functions.
     */
    fun addBuiltins() = synchronized(this) {
        delegate.addBuiltins()
    }
    
    /**
     * Add custom function.
     */
    fun addFunction(id: String, fn: (List<FluentValue>, FluentArgs) -> FluentValue) = synchronized(this) {
        delegate.addFunction(id, fn)
    }
    
    /**
     * Set transform function.
     */
    fun setTransform(fn: (String) -> String) = synchronized(this) {
        delegate.setTransform(fn)
    }
    
    /**
     * Get locales.
     */
    fun locales(): List<LanguageIdentifier> = delegate.locales
    
    /**
     * Get memoizer.
     */
    fun memoizer() = delegate.memoizer()
    
    companion object {
        /**
         * Create a new concurrent FluentBundle.
         */
        @JvmStatic
        fun new(locales: List<LanguageIdentifier>): ConcurrentFluentBundle {
            return ConcurrentFluentBundle(locales)
        }
    }
}
