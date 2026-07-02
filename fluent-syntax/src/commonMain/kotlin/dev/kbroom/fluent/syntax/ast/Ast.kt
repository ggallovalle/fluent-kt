package dev.kbroom.fluent.syntax

import kotlinx.serialization.Serializable

/**
 * Root AST node for an FTL resource.
 */
@Serializable
data class Resource(val body: List<Entry>)

@Serializable
sealed class Entry {
    @Serializable
    data class Message(val id: Identifier, val value: Pattern? = null, val attributes: List<Attribute> = emptyList(), val comment: Comment? = null) : Entry()
    @Serializable
    data class Term(val id: Identifier, val value: Pattern, val attributes: List<Attribute> = emptyList(), val comment: Comment? = null) : Entry()
    @Serializable
    data class Comment(val content: List<String>) : Entry()
    @Serializable
    data class GroupComment(val content: List<String>) : Entry()
    @Serializable
    data class ResourceComment(val content: List<String>) : Entry()
    @Serializable
    data class Junk(val content: String) : Entry()
}

@Serializable
data class Identifier(val name: String)

@Serializable
data class Pattern(val elements: List<PatternElement>)

@Serializable
sealed class PatternElement {
    @Serializable
    data class TextElement(val value: String) : PatternElement()
    @Serializable
    data class Placeable(val expression: Expression) : PatternElement()
}

@Serializable
data class Attribute(val id: Identifier, val value: Pattern)

@Serializable
sealed class Expression {
    @Serializable
    data class Select(val selector: InlineExpression, val variants: List<Variant>) : Expression()
    @Serializable
    data class Inline(val expression: InlineExpression) : Expression()
}

@Serializable
sealed class InlineExpression {
    @Serializable
    data class StringLiteral(val value: String) : InlineExpression()
    @Serializable
    data class NumberLiteral(val value: String) : InlineExpression()
    @Serializable
    data class FunctionReference(val id: Identifier, val arguments: CallArguments) : InlineExpression()
    @Serializable
    data class MessageReference(val id: Identifier, val attribute: Identifier? = null) : InlineExpression()
    @Serializable
    data class TermReference(val id: Identifier, val attribute: Identifier? = null, val arguments: CallArguments? = null) : InlineExpression()
    @Serializable
    data class VariableReference(val id: Identifier) : InlineExpression()
    @Serializable
    data class Placeable(val expression: Expression) : InlineExpression()
}

@Serializable
data class Variant(val key: VariantKey, val value: Pattern, val default: Boolean)

@Serializable
sealed class VariantKey {
    @Serializable
    data class Identifier(val name: String) : VariantKey()
    @Serializable
    data class NumberLiteral(val value: String) : VariantKey()
}

@Serializable
data class CallArguments(val positional: List<InlineExpression> = emptyList(), val named: List<NamedArgument> = emptyList())

@Serializable
data class NamedArgument(val name: Identifier, val value: InlineExpression)
