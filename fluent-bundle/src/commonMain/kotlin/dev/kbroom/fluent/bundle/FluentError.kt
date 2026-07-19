package dev.kbroom.fluent.bundle

/**
 * Errors that can occur in FluentBundle.
 */
@Suppress("AbstractClassCanBeInterface")
sealed class FluentError {
    /**
     * Attempted to override an entry.
     */
    data class Overriding(val kind: EntryKind, val id: String) : FluentError()

    /**
     * Parser errors.
     */
    data class ParserError(val errors: List<Any>) : FluentError()

    /**
     * Resolver errors.
     */
    data class ResolverError(val error: dev.kbroom.fluent.bundle.resolver.ResolverError) : FluentError()
}

/**
 * Kind of entry.
 */
enum class EntryKind {
    MESSAGE,
    TERM,
    FUNCTION,
}
