package dev.kbroom.fluent.syntax.serializer

import dev.kbroom.fluent.syntax.CallArguments
import dev.kbroom.fluent.syntax.DocComment
import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.Expression
import dev.kbroom.fluent.syntax.InlineExpression
import dev.kbroom.fluent.syntax.Pattern
import dev.kbroom.fluent.syntax.PatternElement
import dev.kbroom.fluent.syntax.Resource
/**
 * Options for the [Serializer].
 *
 * @property withJunk If true, serialized junk entries (unparseable content) in the output.
 *                   Typically set to false for clean round-trip serialization.
 */
data class SerializerOptions(val withJunk: Boolean = false)

/**
 * Serializes an AST [Resource] back to a Fluent Translation List (FTL) string.
 *
 * This is useful for debugging, logging, or round-tripping parsed content.
 * The serializer produces canonical FTL formatting.
 *
 * @example
 * ```kotlin
 * val parser = FluentParser()
 * val resource = parser.parse(source)
 * val serializer = Serializer()
 * val output = serializer.serialize(resource)
 * ```
 *
 * @see SerializerOptions for serialization configuration
 */
class Serializer(private val options: SerializerOptions = SerializerOptions()) {

    /**
     * Serializes the given [Resource] to an FTL string.
     *
     * @param resource The AST root to serialize
     * @return A string in Fluent Translation List format
     */
    fun serialize(resource: Resource): String {
        val sb = StringBuilder()
        for (entry in resource.body) {
            serializeEntry(entry, sb)
            // Junk entries already end in a newline (parser preserves
            // original line boundaries); don't add a second one.
            val endsWithNewline = entry is Entry.Junk && entry.content.endsWith("\n")
            if (!endsWithNewline) sb.append("\n")
        }
        return sb.toString()
    }

    private fun serializeEntry(entry: Entry, sb: StringBuilder) {
        when (entry) {
            is Entry.Message -> serializeMessage(entry, sb)

            is Entry.Term -> serializeTerm(entry, sb)

            is Entry.Comment -> serializeComment(entry, sb)

            is Entry.GroupComment -> serializeGroupComment(entry, sb)

            is Entry.ResourceComment -> serializeResourceComment(entry, sb)

            is Entry.Junk -> {
                if (options.withJunk) {
                    sb.append(entry.content)
                }
            }
        }
    }

    private fun serializeMessage(msg: Entry.Message, sb: StringBuilder) {
        // Handle docComment first (overrides inline comment when present)
        msg.docComment?.let { doc ->
            serializeDocComment(doc, "#", sb)
        }

        // Fall back to inline comment if no docComment
        if (msg.docComment == null) {
            msg.comment?.let { comment ->
                val lines = comment.content.lines().filter { it.isNotEmpty() }
                for (line in lines) {
                    sb.append("# $line\n")
                }
            }
        }

        sb.append("${msg.id.name} = ")
        msg.value?.let { pattern ->
            serializePattern(pattern, sb)
        }
        for (attr in msg.attributes) {
            sb.append("\n    .${attr.id.name} = ")
            serializePattern(attr.value, sb)
        }
    }

    private fun serializeTerm(term: Entry.Term, sb: StringBuilder) {
        // Handle docComment first
        term.docComment?.let { doc ->
            serializeDocComment(doc, "#", sb)
        }

        // Fall back to inline comment if no docComment
        if (term.docComment == null) {
            term.comment?.let { comment ->
                val lines = comment.content.lines().filter { it.isNotEmpty() }
                for (line in lines) {
                    sb.append("# $line\n")
                }
            }
        }

        sb.append("-${term.id.name} = ")
        serializePattern(term.value, sb)
        for (attr in term.attributes) {
            sb.append("\n    .${attr.id.name} = ")
            serializePattern(attr.value, sb)
        }
    }

    private fun serializePattern(pattern: Pattern, sb: StringBuilder) {
        for (element in pattern.elements) {
            when (element) {
                is PatternElement.TextElement -> sb.append(element.value)

                is PatternElement.Placeable -> {
                    sb.append("{ ")
                    serializeExpression(element.expression, sb)
                    sb.append(" }")
                }
            }
        }
    }

    private fun serializeExpression(expr: Expression, sb: StringBuilder) {
        when (expr) {
            is Expression.Select -> {
                sb.append("$")
                serializeInlineExpression(expr.selector, sb)
                sb.append(" ->\n")
                for (variant in expr.variants) {
                    sb.append("    [${variant.key}] ")
                    serializePattern(variant.value, sb)
                    sb.append("\n")
                }
            }

            is Expression.Inline -> {
                serializeInlineExpression(expr.expression, sb)
            }
        }
    }

    private fun serializeInlineExpression(expr: InlineExpression, sb: StringBuilder) {
        when (expr) {
            is InlineExpression.StringLiteral -> sb.append("\"${expr.value}\"")

            is InlineExpression.NumberLiteral -> sb.append(expr.value)

            is InlineExpression.MessageReference -> {
                sb.append(expr.id.name)
                expr.attribute?.let { sb.append(".${it.name}") }
            }

            is InlineExpression.TermReference -> {
                sb.append("-${expr.id.name}")
                expr.attribute?.let { sb.append(".${it.name}") }
                expr.arguments?.let { serializeCallArguments(it, sb) }
            }

            is InlineExpression.VariableReference -> sb.append("\$${expr.id.name}")

            is InlineExpression.FunctionReference -> {
                sb.append(expr.id.name)
                serializeCallArguments(expr.arguments, sb)
            }

            is InlineExpression.Placeable -> {
                sb.append("{ ")
                serializeExpression(expr.expression, sb)
                sb.append(" }")
            }
        }
    }

    private fun serializeCallArguments(args: CallArguments, sb: StringBuilder) {
        sb.append("(")
        val positional = args.positional.map {
            when (it) {
                is InlineExpression.StringLiteral -> "\"${it.value}\""
                is InlineExpression.NumberLiteral -> it.value
                is InlineExpression.VariableReference -> "\$${it.id.name}"
                else -> "{...}"
            }
        }
        sb.append(positional.joinToString(", "))
        if (args.named.isNotEmpty()) {
            if (positional.isNotEmpty()) sb.append(", ")
            val named = args.named.map { "${it.name.name}: ${it.value}" }
            sb.append(named.joinToString(", "))
        }
        sb.append(")")
    }

    private fun serializeDocComment(doc: DocComment, prefix: String, sb: StringBuilder) {
        if (doc.description.isNotEmpty()) {
            for (line in doc.description.lines()) {
                sb.append("$prefix $line\n")
            }
        }
        if (doc.variables.isNotEmpty()) {
            sb.append("$prefix Variables:\n")
            for (v in doc.variables) {
                sb.append("$prefix   \$${v.name}")
                if (v.type.isNotEmpty()) sb.append(" (${v.type})")
                if (v.defaultValue.isNotEmpty()) sb.append(" {${v.type}, \"${v.defaultValue}\"}")
                if (v.description.isNotEmpty()) sb.append(": ${v.description}")
                sb.append("\n")
            }
        }
    }

    private fun serializeComment(comment: Entry.Comment, sb: StringBuilder) {
        val lines = comment.content.lines().filter { it.isNotEmpty() }
        for (line in lines) {
            sb.append("# $line\n")
        }
    }

    private fun serializeGroupComment(comment: Entry.GroupComment, sb: StringBuilder) {
        val lines = comment.content.lines().filter { it.isNotEmpty() }
        for (line in lines) {
            sb.append("## $line\n")
        }
    }

    private fun serializeResourceComment(comment: Entry.ResourceComment, sb: StringBuilder) {
        val lines = comment.content.lines().filter { it.isNotEmpty() }
        for (line in lines) {
            sb.append("### $line\n")
        }
    }
}

/**
 * Convenience function to serialize a Resource to an FTL string.
 *
 * @param resource The AST root to serialize
 * @param options Serialization options
 * @return A string in Fluent Translation List format
 */
fun serialize(resource: Resource, options: SerializerOptions = SerializerOptions()): String =
    Serializer(options).serialize(resource)
