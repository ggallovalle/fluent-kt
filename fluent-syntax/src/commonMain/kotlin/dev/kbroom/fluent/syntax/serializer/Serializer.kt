package dev.kbroom.fluent.syntax

import dev.kbroom.fluent.syntax.*

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
        msg.comment?.let { serializeComment(it, sb); sb.append("\n") }
        
        sb.append(msg.id.name)
        sb.append(" = ")
        
        msg.value?.let { serializePattern(it, sb) }
        
        for (attr in msg.attributes) {
            sb.append("\n    .")
            sb.append(attr.id.name)
            sb.append(" = ")
            serializePattern(attr.value, sb)
        }
    }
    
    private fun serializeTerm(term: Entry.Term, sb: StringBuilder) {
        term.comment?.let { serializeComment(it, sb); sb.append("\n") }
        
        sb.append("-")
        sb.append(term.id.name)
        sb.append(" = ")
        serializePattern(term.value, sb)
        
        for (attr in term.attributes) {
            sb.append("\n    .")
            sb.append(attr.id.name)
            sb.append(" = ")
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
                sb.append(" {")
                for (variant in expr.variants) {
                    sb.append("\n    [")
                    serializeVariantKey(variant.key, sb)
                    sb.append("] ")
                    serializePattern(variant.value, sb)
                }
                sb.append("\n}")
            }
            is Expression.Inline -> serializeInlineExpression(expr.expression, sb)
        }
    }
    
    private fun serializeInlineExpression(expr: InlineExpression, sb: StringBuilder) {
        when (expr) {
            is InlineExpression.StringLiteral -> sb.append("\"${expr.value}\"")
            is InlineExpression.NumberLiteral -> sb.append(expr.value)
            is InlineExpression.VariableReference -> sb.append("\$${expr.id.name}")
            is InlineExpression.MessageReference -> {
                sb.append(expr.id.name)
                expr.attribute?.let { sb.append(".${it.name}") }
            }
            is InlineExpression.TermReference -> {
                sb.append("-")
                sb.append(expr.id.name)
                expr.attribute?.let { sb.append(".${it.name}") }
            }
            is InlineExpression.FunctionReference -> {
                sb.append(expr.id.name)
                sb.append("(")
                serializeCallArguments(expr.arguments, sb)
                sb.append(")")
            }
            is InlineExpression.Placeable -> {
                serializeExpression(expr.expression, sb)
            }
        }
    }
    
    private fun serializeCallArguments(args: CallArguments, sb: StringBuilder) {
        var first = true
        for (pos in args.positional) {
            if (!first) sb.append(", ")
            serializeInlineExpression(pos, sb)
            first = false
        }
        for (named in args.named) {
            if (!first) sb.append(", ")
            sb.append(named.name.name)
            sb.append(": ")
            serializeInlineExpression(named.value, sb)
            first = false
        }
    }
    
    private fun serializeVariantKey(key: VariantKey, sb: StringBuilder) {
        when (key) {
            is VariantKey.Identifier -> sb.append(key.name)
            is VariantKey.NumberLiteral -> sb.append(key.value)
        }
    }
    
    private fun serializeComment(comment: Entry.Comment, sb: StringBuilder) {
        for (line in comment.content) {
            sb.append("# ")
            sb.append(line)
            sb.append("\n")
        }
    }
    
    private fun serializeGroupComment(comment: Entry.GroupComment, sb: StringBuilder) {
        for (line in comment.content) {
            sb.append("## ")
            sb.append(line)
            sb.append("\n")
        }
    }
    
    private fun serializeResourceComment(comment: Entry.ResourceComment, sb: StringBuilder) {
        for (line in comment.content) {
            sb.append("### ")
            sb.append(line)
            sb.append("\n")
        }
    }
}

fun serialize(resource: Resource, options: SerializerOptions = SerializerOptions()): String {
    return Serializer(options).serialize(resource)
}
