package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.bundle.types.fluentValueOf

/**
 * FluentArgs wraps arguments passed to message formatting.
 * Supports both named arguments (key-value) and positional arguments.
 * Corresponds to Rust's FluentArgs.
 */
class FluentArgs {
    private val positional: MutableList<FluentValue> = mutableListOf()
    private val named: MutableMap<String, FluentValue> = LinkedHashMap()
    
    /**
     * Add a positional argument.
     */
    fun add(value: FluentValue) {
        positional.add(value)
    }
    
    /**
     * Add a positional argument from any value.
     */
    fun add(value: Any?) {
        positional.add(fluentValueOf(value))
    }
    
    /**
     * Set a named argument.
     */
    fun set(key: String, value: Any?) {
        named[key] = fluentValueOf(value)
    }
    
    /**
     * Get a positional argument by index.
     */
    fun getPositional(index: Int): FluentValue? = positional.getOrNull(index)
    
    /**
     * Get a named argument by key.
     */
    fun get(key: String): FluentValue? = named[key]
    
    /**
     * Get all positional arguments.
     */
    fun positionalArgs(): List<FluentValue> = positional.toList()
    
    /**
     * Get all named arguments as map.
     */
    fun namedArgs(): Map<String, FluentValue> = named.toMap()
    
    /**
     * Get all arguments as map (named only).
     */
    fun toMap(): Map<String, FluentValue> = named.toMap()
    
    /**
     * Check if a named argument exists.
     */
    fun contains(key: String): Boolean = named.containsKey(key)
    
    /**
     * Get the number of positional arguments.
     */
    fun positionalCount(): Int = positional.size
    
    /**
     * Get the number of named arguments.
     */
    fun namedCount(): Int = named.size
}

/**
 * Convenience function to create FluentArgs with named arguments.
 */
fun fluentArgsOf(vararg pairs: Pair<String, Any?>): FluentArgs {
    val args = FluentArgs()
    for ((key, value) in pairs) {
        args.set(key, value)
    }
    return args
}

/**
 * Convenience function to create FluentArgs with positional arguments.
 */
fun fluentArgsOf(vararg values: Any?): FluentArgs {
    val args = FluentArgs()
    for (value in values) {
        args.add(value)
    }
    return args
}
