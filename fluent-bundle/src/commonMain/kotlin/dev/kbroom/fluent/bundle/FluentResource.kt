package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.syntax.*
import dev.kbroom.fluent.syntax.parser.FluentParser

/**
 * FluentResource wraps parsed AST.
 */
class FluentResource(val body: List<Entry>) {
    
    companion object {
        fun tryNew(source: String): Result<FluentResource> {
            return try {
                val parser = FluentParser()
                val resource = parser.parse(source)
                Result.success(FluentResource(resource.body))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    fun messages(): List<Entry.Message> = body.filterIsInstance<Entry.Message>()
    
    fun terms(): List<Entry.Term> = body.filterIsInstance<Entry.Term>()
}
