package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.syntax.Pattern
import java.util.concurrent.locks.ReentrantReadWriteLock
/**
 * Thread-safe FluentBundle for JVM using read-write locking.
 */
class JvmConcurrentFluentBundle(locales: List<LanguageIdentifier>, useIsolating: Boolean = true) {
    private val bundle = FluentBundle(locales, useIsolating)
    private val lock = ReentrantReadWriteLock()

    fun format(id: String, args: FluentArgs? = null): String? {
        lock.readLock().lock()
        try {
            return bundle.format(id, args)
        } finally {
            lock.readLock().unlock()
        }
    }

    fun formatPattern(
        pattern: Pattern,
        args: FluentArgs? = null,
        errors: MutableList<FluentError> = mutableListOf(),
    ): String {
        lock.readLock().lock()
        try {
            return bundle.formatPattern(pattern, args, errors)
        } finally {
            lock.readLock().unlock()
        }
    }

    fun addResource(resource: FluentResource): Result<Unit> {
        lock.writeLock().lock()
        try {
            return bundle.addResource(resource)
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun addResourceOverriding(resource: FluentResource): Result<Unit> {
        lock.writeLock().lock()
        try {
            return bundle.addResourceOverriding(resource)
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun getMessage(id: String): FluentMessage? = bundle.getMessage(id)
    fun hasMessage(id: String): Boolean = bundle.hasMessage(id)
    fun addBuiltins() = bundle.addBuiltins()
    fun locales(): List<LanguageIdentifier> = bundle.locales

    fun addFunction(id: String, fn: (List<FluentValue>, FluentArgs) -> FluentValue) = bundle.addFunction(id, fn)
    fun setTransform(fn: (String) -> String) = bundle.setTransform(fn)
    fun memoizer() = bundle.memoizer()

    companion object {
        @JvmStatic
        fun new(locales: List<LanguageIdentifier>): JvmConcurrentFluentBundle = JvmConcurrentFluentBundle(locales)
    }
}
