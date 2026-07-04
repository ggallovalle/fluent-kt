package dev.kbroom.fluent.syntax

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root AST node for an FTL resource.
 */
@Serializable
data class Resource(val body: List<Entry>)

@Serializable
sealed class Entry {
    @Serializable @SerialName("Message")
    data class Message(val id: Identifier, val value: Pattern? = null, val attributes: List<Attribute> = emptyList(), val comment: Comment? = null) : Entry()
    @Serializable @SerialName("Term")
    data class Term(val id: Identifier, val value: Pattern, val attributes: List<Attribute> = emptyList(), val comment: Comment? = null) : Entry()
    @Serializable @SerialName("Comment")
    data class Comment(val content: List<String>) : Entry()
    @Serializable @SerialName("GroupComment")
    data class GroupComment(val content: List<String>) : Entry()
    @Serializable @SerialName("ResourceComment")
    data class ResourceComment(val content: List<String>) : Entry()
    @Serializable @SerialName("Junk")
    data class Junk(val content: String) : Entry()
}

@Serializable
data class Identifier(val name: String)

@Serializable
data class Pattern(val elements: List<PatternElement>)

@Serializable
sealed class PatternElement {
    @Serializable @SerialName("TextElement")
    data class TextElement(val value: String) : PatternElement()
    @Serializable @SerialName("Placeable")
    data class Placeable(val expression: Expression) : PatternElement()
}

@Serializable
data class Attribute(val id: Identifier, val value: Pattern)

@Serializable
sealed class Expression {
    @Serializable @SerialName("Select")
    data class Select(val selector: InlineExpression, val variants: List<Variant>) : Expression()
    @Serializable @SerialName("Inline")
    data class Inline(val expression: InlineExpression) : Expression()
}

@Serializable
sealed class InlineExpression {
    @Serializable @SerialName("StringLiteral")
    data class StringLiteral(val value: String) : InlineExpression()
    @Serializable @SerialName("NumberLiteral")
    data class NumberLiteral(val value: String) : InlineExpression()
    @Serializable @SerialName("FunctionReference")
    data class FunctionReference(val id: Identifier, val arguments: CallArguments) : InlineExpression()
    @Serializable @SerialName("MessageReference")
    data class MessageReference(val id: Identifier, val attribute: Identifier? = null) : InlineExpression()
    @Serializable @SerialName("TermReference")
    data class TermReference(val id: Identifier, val attribute: Identifier? = null, val arguments: CallArguments? = null) : InlineExpression()
    @Serializable @SerialName("VariableReference")
    data class VariableReference(val id: Identifier) : InlineExpression()
    @Serializable @SerialName("Placeable")
    data class Placeable(val expression: Expression) : InlineExpression()
}

@Serializable
data class Variant(val key: VariantKey, val value: Pattern, val default: Boolean)

@Serializable
sealed class VariantKey {
    @Serializable @SerialName("Identifier")
    data class Identifier(val name: String) : VariantKey()
    @Serializable @SerialName("NumberLiteral")
    data class NumberLiteral(val value: String) : VariantKey()
}

@Serializable
data class CallArguments(val positional: List<InlineExpression> = emptyList(), val named: List<NamedArgument> = emptyList())

@Serializable
data class NamedArgument(val name: Identifier, val value: InlineExpression)

