package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.syntax.Attribute
import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.Pattern

/**
 * FluentMessage provides a public view of a message.
 */
class FluentMessage(private val message: Entry.Message) {

    fun id(): String = message.id.name

    fun value(): Pattern? = message.value

    fun attributes(): List<Attribute> = message.attributes

    /**
     * Attribute names keyed by their raw [Pattern]. Corresponds to the
     * "names and resolved values" view used by tooling that just wants a
     * catalog (insertion order preserved, matching the AST).
     *
     * If you need a formatted string per attribute, call
     * `bundle.formatPattern(pattern, args)` against the returned values.
     */
    fun attributesMap(): Map<String, Pattern> =
        message.attributes.associate { it.id.name to it.value }

    fun getAttribute(name: String): Attribute? = message.attributes.find { it.id.name == name }

    /**
     * Get the value Pattern of an attribute by name.
     */
    fun getAttributeValue(name: String): Pattern? = getAttribute(name)?.value

    fun comment(): Entry.Comment? = message.comment

    fun hasValue(): Boolean = message.value != null

    fun hasAttributes(): Boolean = message.attributes.isNotEmpty()
}

/**
 * FluentTerm provides a public view of a term.
 */
class FluentTerm(private val term: Entry.Term) {

    fun id(): String = term.id.name

    fun value(): Pattern = term.value

    fun attributes(): List<Attribute> = term.attributes

    /**
     * Attribute names keyed by their raw [Pattern] (insertion order preserved).
     */
    fun attributesMap(): Map<String, Pattern> =
        term.attributes.associate { it.id.name to it.value }

    fun getAttribute(name: String): Attribute? = term.attributes.find { it.id.name == name }

    fun getAttributeValue(name: String): Pattern? = getAttribute(name)?.value

    fun comment(): Entry.Comment? = term.comment
}
