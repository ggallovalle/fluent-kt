package dev.kbroom.fluent.syntax.parser

import dev.kbroom.fluent.syntax.*

/**
 * Full parser for Fluent Translation Lists (FTL).
 */
class FluentParser {
    
    private var pos = 0
    private var source = ""
    private val errors = mutableListOf<ParserError>()
    
    fun parse(source: String): Resource {
        this.source = source
        this.pos = 0
        this.errors.clear()
        
        val body = mutableListOf<Entry>()
        
        while (pos < source.length) {
            skipWhitespace()
            if (pos >= source.length) break
            
            // Check for comment
            when {
                peek() == '#' -> {
                    body.add(parseComment())
                }
                peek() == '-' -> {
                    body.add(parseTerm())
                }
                isIdentifierStart(peek()) -> {
                    body.add(parseMessage())
                }
                else -> {
                    // Junk
                    val start = pos
                    skipToNewline()
                    val content = source.substring(start, pos).trim()
                    if (content.isNotEmpty()) {
                        errors.add(ParserError.Error(ErrorKind.InvalidToken, "Invalid token", Span(start, pos, source)))
                    }
                }
            }
        }
        
        return Resource(body)
    }
    
    private fun parseComment(): Entry {
        val start = pos
        val content = mutableListOf<String>()
        
        // Skip the first #
        if (peek() == '#') {
            pos++
            // Check for second #
            if (peek() == '#') {
                pos++
                // Check for third #
                if (peek() == '#') {
                    pos++
                    // Resource comment
                    val line = parseCommentLine()
                    return Entry.ResourceComment(listOf(line))
                }
                // Group comment
                val line = parseCommentLine()
                return Entry.GroupComment(listOf(line))
            }
            // Regular comment
            val line = parseCommentLine()
            return Entry.Comment(listOf(line))
        }
        
        return Entry.Comment(listOf(""))
    }
    
    private fun parseCommentLine(): String {
        val start = pos
        skipToNewline()
        return source.substring(start, pos).trim()
    }
    
    private fun parseMessage(): Entry {
        val start = pos
        val id = parseIdentifier()
        
        skipWhitespace()
        
        if (peek() != '=') {
            errors.add(ParserError.Error(ErrorKind.MissingField, "Expected '=' after message identifier", Span(start, pos, source)))
            return Entry.Junk(source.substring(start, minOf(pos + 10, source.length)))
        }
        pos++ // skip =
        
        skipWhitespace()
        
        // Parse as pattern - Fluent allows inline patterns without outer braces
        // as long as they contain placeables or are explicitly marked
        // For now, parse all values as patterns to handle placeables correctly
        val value = parsePattern()
        
        /*
        val value = if (peek() == '{') {
            parsePattern()
        } else {
            // Simple string value
            val valueStart = pos
            while (pos < source.length && peek() != '\n') {
                pos++
            }
            val text = source.substring(valueStart, pos).trim()
            if (text.isNotEmpty()) {
                Pattern(listOf(PatternElement.TextElement(text)))
            } else {
                null
            }
        }
        */
        
        val attributes = mutableListOf<Attribute>()
        while (true) {
            skipWhitespace()
            if (peek() != '.') break
            pos++ // skip .
            skipWhitespace()
            val attrId = parseIdentifier()
            skipWhitespace()
            if (peek() != '=') {
                errors.add(ParserError.Error(ErrorKind.MissingField, "Expected '=' after attribute identifier", Span(pos, pos, source)))
                break
            }
            pos++ // skip =
            skipWhitespace()
            val attrValue = parsePattern()
            attributes.add(Attribute(Identifier(attrId), attrValue))
        }
        
        val comment = parseInlineComment()
        
        return Entry.Message(Identifier(id), value, attributes, comment)
    }
    
    private fun parseTerm(): Entry.Term {
        val start = pos
        pos++ // skip -
        
        val id = parseIdentifier()
        
        skipWhitespace()
        
        if (peek() != '=') {
            errors.add(ParserError.Error(ErrorKind.MissingField, "Expected '=' after term identifier", Span(start, pos, source)))
            return Entry.Term(Identifier(id), Pattern(listOf(PatternElement.TextElement(""))), emptyList(), null)
        }
        pos++ // skip =
        
        skipWhitespace()
        
        val value = parsePattern()
        
        val attributes = mutableListOf<Attribute>()
        while (true) {
            skipWhitespace()
            if (peek() != '.') break
            pos++ // skip .
            skipWhitespace()
            val attrId = parseIdentifier()
            skipWhitespace()
            if (peek() != '=') break
            pos++ // skip =
            skipWhitespace()
            val attrValue = parsePattern()
            attributes.add(Attribute(Identifier(attrId), attrValue))
        }
        
        val comment = parseInlineComment()
        
        return Entry.Term(Identifier(id), value, attributes, comment)
    }
    
    private fun parsePattern(): Pattern {
        val elements = mutableListOf<PatternElement>()
        
        while (pos < source.length && peek() != '\n') {
            when {
                peek() == '{' -> {
                    pos++ // skip {
                    skipWhitespace()
                    if (peek() == '}') {
                        pos++ // skip }
                        continue
                    }
                    val expr = parseExpression()
                    skipWhitespace()
                    if (peek() == '}') {
                        pos++ // skip }
                    }
                    elements.add(PatternElement.Placeable(expr))
                }
                else -> {
                    val textStart = pos
                    while (pos < source.length && peek() != '{' && peek() != '\n') {
                        pos++
                    }
                    val text = source.substring(textStart, pos)
                    if (text.isNotEmpty()) {
                        elements.add(PatternElement.TextElement(text))
                    }
                }
            }
        }
        
        // Dedent multiline values
        val dedented = dedentPattern(elements)
        return Pattern(dedented)
    }
    
    private fun dedentPattern(elements: List<PatternElement>): List<PatternElement> {
        if (elements.isEmpty()) return elements
        
        // Find common leading whitespace
        var minIndent = Int.MAX_VALUE
        for (element in elements) {
            if (element is PatternElement.TextElement) {
                val lines = element.value.split('\n')
                for (i in 1 until lines.size) {
                    val line = lines[i]
                    val indent = line.takeWhile { it == ' ' }.length
                    if (line.isNotEmpty()) {
                        minIndent = minOf(minIndent, indent)
                    }
                }
            }
        }
        
        if (minIndent == Int.MAX_VALUE || minIndent == 0) return elements
        
        // Remove common indent
        return elements.map { element ->
            if (element is PatternElement.TextElement) {
                val lines = element.value.split('\n')
                val dedented = lines.mapIndexed { i, line ->
                    if (i == 0 || line.isEmpty()) line
                    else line.drop(minIndent)
                }.joinToString("\n")
                PatternElement.TextElement(dedented)
            } else element
        }
    }
    
    private fun parseExpression(): Expression {
        // Check for select expression
        skipWhitespace()
        if (peek() == '[' || peek() == '*') {
            // Standalone select - create a pseudo-selector
            val variants = parseVariants()
            // Use a literal selector for standalone select
            return Expression.Select(InlineExpression.StringLiteral(""), variants)
        }
        
        // Check if we have a select (selector followed by variants)
        skipWhitespace()
        
        // Inline expression
        val inlineExpr = parseInlineExpression()
        
        skipWhitespace()
        if (peek() == '[' || peek() == '*') {
            // It's a select expression
            val variants = parseVariants()
            return Expression.Select(inlineExpr, variants)
        }
        
        return Expression.Inline(inlineExpr)
    }
    
    private fun parseVariants(): List<Variant> {
        val variants = mutableListOf<Variant>()
        
        while (peek() == '[' || peek() == '*') {
            val default = (peek() == '*')
            if (peek() == '*') pos++
            
            // Skip [
            if (peek() == '[') pos++
            
            skipWhitespace()
            val key = parseVariantKey()
            
            skipWhitespace()
            
            // Skip ]
            if (peek() == ']') pos++
            
            skipWhitespace()
            val value = parsePattern()
            
            variants.add(Variant(key, value, default))
        }
        
        return variants
    }
    
    private fun parseVariantKey(): VariantKey {
        val start = pos
        
        when {
            peek() == '"' -> {
                pos++ // skip opening "
                val keyStart = pos
                while (pos < source.length && peek() != '"') {
                    if (peek() == '\\') pos++ // skip escape
                    pos++
                }
                val value = source.substring(keyStart, pos)
                if (peek() == '"') pos++ // skip closing "
                return VariantKey.NumberLiteral(value)
            }
            peek() == '-' -> {
                // Negative number
                pos++
                val numStart = pos
                while (pos < source.length && (peek().isDigit() || peek() == '.')) {
                    pos++
                }
                return VariantKey.NumberLiteral("-" + source.substring(numStart, pos))
            }
            peek().isDigit() -> {
                val numStart = pos
                while (pos < source.length && (peek().isDigit() || peek() == '.')) {
                    pos++
                }
                return VariantKey.NumberLiteral(source.substring(numStart, pos))
            }
            else -> {
                val name = parseIdentifier()
                return VariantKey.Identifier(name)
            }
        }
    }
    
    private fun parseInlineExpression(): InlineExpression {
        return when {
            peek() == '$' -> parseVariableReference()
            peek() == '-' -> parseTermOrFunctionReference()
            peek() == '"' -> parseStringLiteral()
            peek().isDigit() || peek() == '-' -> parseNumberLiteral()
            isIdentifierStart(peek()) -> parseMessageOrFunctionReference()
            peek() == '(' -> {
                pos++ // skip (
                val expr = parseExpression()
                skipWhitespace()
                if (peek() == ')') pos++
                InlineExpression.Placeable(expr)
            }
            else -> {
                val start = pos
                skipToNewline()
                InlineExpression.StringLiteral(source.substring(start, pos))
            }
        }
    }
    
    private fun parseVariableReference(): InlineExpression.VariableReference {
        pos++ // skip $
        val id = parseIdentifier()
        return InlineExpression.VariableReference(Identifier(id))
    }
    
    private fun parseTermOrFunctionReference(): InlineExpression {
        val start = pos
        pos++ // skip -
        
        if (!isIdentifierStart(peek())) {
            // Just a minus sign
            return InlineExpression.StringLiteral("-")
        }
        
        val name = parseIdentifier()
        
        // Check for attribute access
        skipWhitespace()
        val attribute = if (peek() == '.') {
            pos++ // skip .
            skipWhitespace()
            Identifier(parseIdentifier())
        } else null
        
        // Check for function arguments
        skipWhitespace()
        val arguments = if (peek() == '(') {
            parseCallArguments()
        } else null
        
        return if (arguments != null) {
            InlineExpression.FunctionReference(Identifier(name), arguments)
        } else if (attribute != null) {
            InlineExpression.TermReference(Identifier(name), attribute, null)
        } else {
            InlineExpression.TermReference(Identifier(name), null, null)
        }
    }
    
    private fun parseMessageOrFunctionReference(): InlineExpression {
        val name = parseIdentifier()
        
        // Check for attribute access
        skipWhitespace()
        val attribute = if (peek() == '.') {
            pos++ // skip .
            skipWhitespace()
            Identifier(parseIdentifier())
        } else null
        
        // Check for function arguments
        skipWhitespace()
        val arguments = if (peek() == '(') {
            parseCallArguments()
        } else null
        
        return if (arguments != null) {
            InlineExpression.FunctionReference(Identifier(name), arguments)
        } else if (attribute != null) {
            InlineExpression.MessageReference(Identifier(name), attribute)
        } else {
            InlineExpression.MessageReference(Identifier(name), null)
        }
    }
    
    private fun parseStringLiteral(): InlineExpression.StringLiteral {
        pos++ // skip opening "
        val start = pos
        while (pos < source.length && peek() != '"') {
            if (peek() == '\\') pos++ // skip escape
            pos++
        }
        val value = source.substring(start, pos)
        if (peek() == '"') pos++ // skip closing "
        return InlineExpression.StringLiteral(value)
    }
    
    private fun parseNumberLiteral(): InlineExpression.NumberLiteral {
        val start = pos
        if (peek() == '-') pos++
        while (pos < source.length && (peek().isDigit() || peek() == '.' || peek() == 'e' || peek() == 'E' || peek() == '+' || peek() == '-')) {
            pos++
        }
        return InlineExpression.NumberLiteral(source.substring(start, pos))
    }
    
    private fun parseCallArguments(): CallArguments {
        pos++ // skip (
        
        val positional = mutableListOf<InlineExpression>()
        val named = mutableListOf<NamedArgument>()
        
        while (peek() != ')' && pos < source.length) {
            skipWhitespace()
            if (peek() == ')') break
            
            if (isIdentifierStart(peek())) {
                val name = parseIdentifier()
                skipWhitespace()
                if (peek() == ':') {
                    // Named argument
                    pos++ // skip :
                    skipWhitespace()
                    val value = parseInlineExpression()
                    named.add(NamedArgument(Identifier(name), value))
                } else {
                    // Positional argument
                    val value = parseInlineExpression()
                    positional.add(value)
                }
            } else {
                val value = parseInlineExpression()
                positional.add(value)
            }
            
            skipWhitespace()
            if (peek() == ',') {
                pos++
            }
        }
        
        if (peek() == ')') pos++ // skip )
        
        return CallArguments(positional, named)
    }
    
    private fun parseIdentifier(): String {
        val start = pos
        while (pos < source.length && isIdentifierPart(peek())) {
            pos++
        }
        return source.substring(start, pos)
    }
    
    private fun parseInlineComment(): Entry.Comment? {
        skipWhitespace()
        if (peek() != '#') return null
        pos++ // skip #
        val line = parseCommentLine()
        return Entry.Comment(listOf(line))
    }
    
    private fun skipWhitespace() {
        while (pos < source.length && (source[pos] == ' ' || source[pos] == '\t' || source[pos] == '\n' || source[pos] == '\r')) {
            pos++
        }
    }
    
    private fun skipToNewline() {
        while (pos < source.length && source[pos] != '\n') {
            pos++
        }
    }
    
    private fun peek(): Char = source.getOrNull(pos) ?: '\u0000'
    
    private fun isIdentifierStart(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c == '_'
    
    private fun isIdentifierPart(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '-'
    
    fun parseRuntime(source: String): Resource {
        val resource = parse(source)
        val filteredBody = resource.body.filter { 
            when (it) {
                is Entry.Comment -> false
                is Entry.GroupComment -> false
                is Entry.ResourceComment -> false
                else -> true
            }
        }
        return Resource(filteredBody)
    }
}

sealed class ParserError {
    data class Error(val kind: ErrorKind, val message: String, val span: Span? = null) : ParserError()
    data class Warning(val kind: ErrorKind, val message: String, val span: Span? = null) : ParserError()
}

sealed class ErrorKind {
    data object MissingField : ErrorKind()
    data object InvalidIdentifier : ErrorKind()
    data object InvalidToken : ErrorKind()
    data object UnexpectedToken : ErrorKind()
}

data class Span(val start: Int, val end: Int, val sourceText: String)
