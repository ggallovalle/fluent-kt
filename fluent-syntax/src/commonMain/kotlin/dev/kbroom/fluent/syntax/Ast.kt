package dev.kbroom.fluent.syntax

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root AST node representing a complete Fluent Translation List (FTL) resource.
 *
 * A Resource is the top-level container for all parsed Fluent content.
 * It contains a list of [Entry] elements representing messages, terms, and comments.
 *
 * @property body The list of entries (messages, terms, comments) in this resource
 */
@Serializable
data class Resource(val body: List<Entry>)

/**
 * Represents a single top-level element in an FTL resource.
 *
 * Entries can be messages, terms, comments, or junk (unparseable content).
 * The Fluent specification defines the following entry types:
 * - [Message] - A translatable string with optional variants and attributes
 * - [Term] - A reusable value that can be referenced by other messages
 * - [Comment] - A single-line comment (starting with #)
 * - [GroupComment] - A section comment (starting with ##)
 * - [ResourceComment] - A file-level comment (starting with ###)
 * - [Junk] - Content that could not be parsed
 */
@Serializable
@Suppress("AbstractClassCanBeInterface")
sealed class Entry {
    /**
     * A message entry - the primary building block of Fluent translations.
     *
     * Messages have an [id] and optional [value] (the translatable text),
     * [attributes] (variant-specific values), and a [comment].
     *
     * @property id The message identifier
     * @property value The message value (pattern), or null if message has no content
     * @property attributes List of attributes (variant-specific values)
     * @property value A comment associated with this message
     */
    @Serializable
    @SerialName("Message")
    data class Message(
        val id: Identifier,
        val value: Pattern? = null,
        val attributes: List<Attribute> = emptyList(),
        val comment: Comment? = null,
        val docComment: DocComment? = null,
    ) : Entry()

    /**
     * A term entry - a reusable value that can be referenced by other messages.
     *
     * Terms are similar to messages but start with a dash (-) and can be
     * referenced using the {-termId} syntax.
     *
     * @property id The term identifier (without the leading dash)
     * @property value The term value (required pattern)
     * @property attributes List of attributes
     * @property comment A comment associated with this term
     */
    @Serializable
    @SerialName("Term")
    data class Term(
        val id: Identifier,
        val value: Pattern,
        val attributes: List<Attribute> = emptyList(),
        val comment: Comment? = null,
        val docComment: DocComment? = null,
    ) : Entry()

    /**
     * A single-line comment (lines starting with #).
     *
     * @property content The comment text, may span multiple lines if # is repeated
     */
    @Serializable
    @SerialName("Comment")
    data class Comment(val content: String) : Entry()

    /**
     * A section comment (lines starting with ##).
     *
     * @property content The group comment text
     */
    @Serializable
    @SerialName("GroupComment")
    data class GroupComment(val content: String) : Entry()

    /**
     * A file-level comment (lines starting with ###).
     *
     * @property content The resource comment text
     */
    @Serializable
    @SerialName("ResourceComment")
    data class ResourceComment(val content: String) : Entry()

    /**
     * Junk - content that could not be parsed as a valid entry.
     *
     * The parser preserves junk content to enable round-tripping of
     * files that contain non-Fluent content.
     *
     * @property content The raw unparseable content
     */
    @Serializable
    @SerialName("Junk")
    data class Junk(val content: String) : Entry()
}

/**
 * A documentation comment associated with a message or term.
 *
 * DocComments contain a description and optionally documented variables
 * for the message/term.
 *
 * @property description The main description text
 * @property variables List of variable documentations
 */
@Serializable
data class DocComment(val description: String = "", val variables: List<VariableDoc> = emptyList())

/**
 * Documentation for a single variable in a message or term.
 *
 * @property name The variable name (without $ prefix)
 * @property type The variable type (e.g., "String", "Number"), or empty if not specified
 * @property description The variable description
 * @property defaultValue Default value for the variable (e.g., "Arial" for {string, "Arial"})
 */
@Serializable
data class VariableDoc(
    val name: String,
    val type: String = "",
    val description: String = "",
    val defaultValue: String = "",
)

/**
 * Represents an identifier (name) in Fluent.
 *
 * Identifiers are used for message IDs, term IDs, attribute names, and variable names.
 *
 * @property name The identifier name string
 */
@Serializable
data class Identifier(val name: String)

/**
 * A pattern - the content of a message or term value.
 *
 * Patterns contain a list of [PatternElement]s which can be either text
 * or placeables (expressions in curly braces).
 *
 * @property elements The list of pattern elements (text and placeables)
 */
@Serializable
data class Pattern(val elements: List<PatternElement>)

/**
 * An element within a [Pattern].
 *
 * Pattern elements are either literal text or placeables (expressions).
 */
@Serializable
@Suppress("AbstractClassCanBeInterface")
sealed class PatternElement {
    /**
     * A literal text element.
     *
     * @property value The text content
     */
    @Serializable
    @SerialName("TextElement")
    data class TextElement(val value: String) : PatternElement()

    /**
     * A placeable - an expression wrapped in curly braces.
     *
     * Placeables can contain variables, function calls, or selectors.
     *
     * @property expression The expression inside the placeable
     */
    @Serializable
    @SerialName("Placeable")
    data class Placeable(val expression: Expression) : PatternElement()
}

/**
 * An attribute - a named variant of a message or term.
 *
 * Attributes are used for gendered variants, plural variants, etc.
 *
 * @property id The attribute identifier
 * @property value The attribute value (pattern)
 */
@Serializable
data class Attribute(val id: Identifier, val value: Pattern)

/**
 * An expression in a pattern or placeable.
 *
 * Expressions can be either inline values or select expressions
 * that choose between variants based on a selector value.
 */
@Serializable
@Suppress("AbstractClassCanBeInterface")
sealed class Expression {
    /**
     * A select expression - chooses between variants based on a selector.
     *
     * Select expressions are used for pluralization and other conditional logic.
     *
     * @property selector The expression to evaluate for selection
     * @property variants The list of possible variants
     */
    @Serializable
    @SerialName("Select")
    data class Select(val selector: InlineExpression, val variants: List<Variant>) : Expression()

    /**
     * An inline expression - a simple value.
     *
     * @property expression The underlying inline expression
     */
    @Serializable
    @SerialName("Inline")
    data class Inline(val expression: InlineExpression) : Expression()
}

/**
 * An inline expression - a simple value that can appear in patterns.
 *
 * Inline expressions include literals, references, and function calls.
 * They cannot contain select expressions.
 */
@Serializable
@Suppress("AbstractClassCanBeInterface")
sealed class InlineExpression {
    /** A string literal value. */
    @Serializable
    @SerialName("StringLiteral")
    data class StringLiteral(val value: String) : InlineExpression()

    /** A number literal value. */
    @Serializable
    @SerialName("NumberLiteral")
    data class NumberLiteral(val value: String) : InlineExpression()

    /**
     * A function reference - a built-in or custom function call.
     *
     * @property id The function name
     * @property arguments The function arguments
     */
    @Serializable
    @SerialName("FunctionReference")
    data class FunctionReference(val id: Identifier, val arguments: CallArguments) : InlineExpression()

    /**
     * A reference to another message.
     *
     * @property id The message identifier
     * @property attribute Optional attribute name for accessing message attributes
     */
    @Serializable
    @SerialName("MessageReference")
    data class MessageReference(val id: Identifier, val attribute: Identifier? = null) : InlineExpression()

    /**
     * A reference to a term.
     *
     * @property id The term identifier (without leading dash)
     * @property attribute Optional attribute name
     * @property arguments Optional arguments for parameterized terms
     */
    @Serializable
    @SerialName("TermReference")
    data class TermReference(
        val id: Identifier,
        val attribute: Identifier? = null,
        val arguments: CallArguments? = null,
    ) : InlineExpression()

    /**
     * A variable reference - references a runtime value.
     *
     * @property id The variable name (without $ prefix)
     */
    @Serializable
    @SerialName("VariableReference")
    data class VariableReference(val id: Identifier) : InlineExpression()

    /**
     * A placeable wrapper for an expression.
     *
     * This allows expressions to appear in contexts where placeables are expected.
     *
     * @property expression The wrapped expression
     */
    @Serializable
    @SerialName("Placeable")
    data class Placeable(val expression: Expression) : InlineExpression()
}

/**
 * A variant in a select expression.
 *
 * @property key The variant key (identifier or number)
 * @property value The variant value (pattern)
 * @property default True if this is the default variant
 */
@Serializable
data class Variant(val key: VariantKey, val value: Pattern, val default: Boolean)

/**
 * The key for a variant in a select expression.
 */
@Serializable
@Suppress("AbstractClassCanBeInterface")
sealed class VariantKey {
    /** An identifier-based variant key (e.g., [one], [few], [many]). */
    @Serializable
    @SerialName("Identifier")
    data class Identifier(val name: String) : VariantKey()

    /** A number-based variant key (e.g., [1], [2]). */
    @Serializable
    @SerialName("NumberLiteral")
    data class NumberLiteral(val value: String) : VariantKey()
}

/**
 * Arguments passed to a function call or term reference.
 *
 * @property positional Positional arguments in order
 * @property named Named arguments (key-value pairs)
 */
@Serializable
data class CallArguments(
    val positional: List<InlineExpression> = emptyList(),
    val named: List<NamedArgument> = emptyList(),
)

/**
 * A named argument (key-value pair) in a function call.
 *
 * @property name The argument name
 * @property value The argument value
 */
@Serializable
data class NamedArgument(val name: Identifier, val value: InlineExpression)
