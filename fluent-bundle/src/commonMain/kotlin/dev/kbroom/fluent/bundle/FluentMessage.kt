package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.syntax.*

/**
 * FluentMessage provides a public view of a message.
 */
class FluentMessage(private val message: Entry.Message) {
    
    fun id(): String = message.id.name
    
    fun value(): Pattern? = message.value
    
    fun attributes(): List<Attribute> = message.attributes
    
    fun getAttribute(name: String): Attribute? {
        return message.attributes.find { it.id.name == name }
    }
    
    /**
     * Get the value Pattern of an attribute by name.
     */
    fun getAttributeValue(name: String): Pattern? {
        return getAttribute(name)?.value
    }
    
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
    
    fun getAttribute(name: String): Attribute? {
        return term.attributes.find { it.id.name == name }
    }
    /**
     * Get the value Pattern of an attribute by name.
     */
    fun getAttributeValue(name: String): Pattern? {
        return getAttribute(name)?.value
    }
    
    fun comment(): Entry.Comment? = term.comment
}
