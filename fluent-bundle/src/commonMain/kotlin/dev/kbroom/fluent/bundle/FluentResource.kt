package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.parser.FluentParser

/**
 * FluentResource wraps a parsed Fluent AST.
 *
 * This is the runtime representation of a parsed .ftl file.
 * It holds the list of entries (messages, terms, comments) extracted from parsing.
 *
 * Use [tryNew] to parse FTL source code into a FluentResource.
 *
 * @property body The list of entries in this resource (messages, terms, comments)
 * @see FluentBundle for the main runtime class
 * @see FluentParser for parsing
 */
class FluentResource(val body: List<Entry>) {

    companion object {
        /**
         * Try to parse a FTL source string into a [FluentResource].
         *
         * @param source The FTL source text
         * @return Result containing the FluentResource on success, or failure with parse error
         */
        fun tryNew(source: String): Result<FluentResource> = try {
            val parser = FluentParser()
            val resource = parser.parse(source)
            Result.success(FluentResource(resource.body))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all messages in this resource.
     *
     * @return List of message entries
     */
    fun messages(): List<Entry.Message> = body.filterIsInstance<Entry.Message>()

    /**
     * Get all terms in this resource.
     *
     * @return List of term entries
     */
    fun terms(): List<Entry.Term> = body.filterIsInstance<Entry.Term>()
}
