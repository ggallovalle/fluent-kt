package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.bundle.types.fluentValueOf

/**
 * FluentArgs wraps arguments passed to message formatting.
 * Corresponds to Rust's FluentArgs.
 */
class FluentArgs {
    private val args: MutableMap<String, FluentValue> = LinkedHashMap()
    
    /**
     * Set a named argument.
     */
    fun set(key: String, value: Any?) {
        args[key] = fluentValueOf(value)
    }
    
    /**
     * Get an argument by name.
     */
    fun get(key: String): FluentValue? = args[key]
    
    /**
     * Get all arguments as map.
     */
    fun toMap(): Map<String, FluentValue> = args.toMap()
    
    /**
     * Check if an argument exists.
     */
    fun contains(key: String): Boolean = args.containsKey(key)
}

/**
 * Convenience function to create FluentArgs.
 */
fun fluentArgsOf(vararg pairs: Pair<String, Any?>): FluentArgs {
    val args = FluentArgs()
    for ((key, value) in pairs) {
        args.set(key, value)
    }
    return args
}
