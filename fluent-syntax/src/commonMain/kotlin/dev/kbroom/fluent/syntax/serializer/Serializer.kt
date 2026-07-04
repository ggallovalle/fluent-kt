package dev.kbroom.fluent.syntax

import dev.kbroom.fluent.syntax.CallArguments
import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.Expression
import dev.kbroom.fluent.syntax.Identifier
import dev.kbroom.fluent.syntax.InlineExpression
import dev.kbroom.fluent.syntax.Pattern
import dev.kbroom.fluent.syntax.PatternElement
import dev.kbroom.fluent.syntax.Resource
import dev.kbroom.fluent.syntax.VariantKey


/**
 * Serializer options.
 */
data class SerializerOptions(
    val withJunk: Boolean = false
)

/**
 * Serialize an AST Resource back to FTL string.
 */
class Serializer {
    
    private val options: SerializerOptions
    
    constructor(options: SerializerOptions = SerializerOptions()) {
        this.options = options
    }
    
    fun serialize(resource: Resource): String {
        val sb = StringBuilder()
        for (entry in resource.body) {
            serializeEntry(entry, sb)
            sb.append("\n")
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
        if (msg.comment != null) {
            for (line in msg.comment.content) {
                sb.append("# $line\n")
            }
        }
        sb.append("${msg.id.name} = ")
        if (msg.value != null) {
            serializePattern(msg.value, sb)
        }
        for (attr in msg.attributes) {
            sb.append("\n    .${attr.id.name} = ")
            serializePattern(attr.value, sb)
        }
    }
    
    private fun serializeTerm(term: Entry.Term, sb: StringBuilder) {
        if (term.comment != null) {
            for (line in term.comment.content) {
                sb.append("# $line\n")
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
                sb.append("\$")
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
                if (expr.attribute != null) {
                    sb.append(".${expr.attribute.name}")
                }
            }
            is InlineExpression.TermReference -> {
                sb.append("-${expr.id.name}")
                if (expr.attribute != null) {
                    sb.append(".${expr.attribute.name}")
                }
                if (expr.arguments != null) {
                    serializeCallArguments(expr.arguments, sb)
                }
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
    
    private fun serializeVariantKey(key: VariantKey, sb: StringBuilder) {
        when (key) {
            is VariantKey.Identifier -> sb.append(key.name)
            is VariantKey.NumberLiteral -> sb.append(key.value)
        }
    }
    
    private fun serializeComment(comment: Entry.Comment, sb: StringBuilder) {
        for (line in comment.content) {
            sb.append("# $line\n")
        }
    }
    
    private fun serializeGroupComment(comment: Entry.GroupComment, sb: StringBuilder) {
        for (line in comment.content) {
            sb.append("## $line\n")
        }
    }
    
    private fun serializeResourceComment(comment: Entry.ResourceComment, sb: StringBuilder) {
        for (line in comment.content) {
            sb.append("### $line\n")
        }
    }
}

fun serialize(resource: Resource, options: SerializerOptions = SerializerOptions()): String {
    return Serializer(options).serialize(resource)
}
