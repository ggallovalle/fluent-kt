package dev.kbroom.fluent.syntax.parser

import dev.kbroom.fluent.syntax.Attribute
import dev.kbroom.fluent.syntax.CallArguments
import dev.kbroom.fluent.syntax.DocComment
import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.Expression
import dev.kbroom.fluent.syntax.Identifier
import dev.kbroom.fluent.syntax.InlineExpression
import dev.kbroom.fluent.syntax.NamedArgument
import dev.kbroom.fluent.syntax.Pattern
import dev.kbroom.fluent.syntax.PatternElement
import dev.kbroom.fluent.syntax.Resource
import dev.kbroom.fluent.syntax.VariableDoc
import dev.kbroom.fluent.syntax.Variant
import dev.kbroom.fluent.syntax.VariantKey

/**
 * A parser for Fluent Translation Lists (FTL).
 *
 * This parser converts FTL source text into an AST [Resource].
 * It is a hand-written recursive descent parser that follows the
 * Fluent specification.
 *
 * The parser collects errors during parsing in the [errors] list.
 * Errors do not stop parsing - the parser is fault-tolerant and will
 * mark problematic sections as [Entry.Junk].
 *
 * ## Usage
 * ```kotlin
 * val parser = FluentParser()
 * val resource = parser.parse(ftlSource)
 * for (error in parser.errors) {
 *     // Handle parsing error
 * }
 * ```
 *
 * ## Error Handling
 * The parser is designed to be fault-tolerant - it will continue parsing
 * even after encountering errors, marking problematic sections as [Entry.Junk].
 * This allows partial parsing results even when the input contains errors.
 *
 * @see Resource
 * @see Entry
 * @see ParserError
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
        var bufferedComment: Entry? = null

        while (pos < source.length) {
            // Check for blank line BEFORE skipping whitespace
            if (peek() == '\n' || peek() == '\r') {
                if (bufferedComment != null) {
                    body.add(bufferedComment)
                    bufferedComment = null
                }
                pos++
                continue
            }

            skipWhitespace()

            // Check for blank line after whitespace
            if (peek() == '\n' || peek() == '\r') {
                if (bufferedComment != null) {
                    body.add(bufferedComment)
                    bufferedComment = null
                }
                pos++
                continue
            }

            if (pos >= source.length) break

            // Check for comment
            when {
                peek() == '#' -> {
                    bufferedComment = parseComment()
                }

                peek() == '-' -> {
                    val termEntry = parseTerm()
                    val boundTerm = bindDocCommentToTerm(termEntry, bufferedComment)
                    body.add(boundTerm)
                    bufferedComment = null
                }

                isIdentifierStart(peek()) -> {
                    val messageEntry = parseMessage()
                    if (messageEntry is Entry.Message) {
                        val boundMessage = bindDocCommentToMessage(messageEntry, bufferedComment)
                        body.add(boundMessage)
                    } else {
                        body.add(messageEntry)
                    }
                    bufferedComment = null
                }

                else -> {
                    // Junk
                    if (bufferedComment != null) {
                        body.add(bufferedComment)
                        bufferedComment = null
                    }
                    val start = pos
                    skipToNewline()
                    val content = source.substring(start, pos).trim()
                    if (content.isNotEmpty()) {
                        errors.add(ParserError.Error(ErrorKind.InvalidToken, "Invalid token", Span(start, pos, source)))
                    }
                }
            }
        }

        // Flush any remaining buffered comment
        if (bufferedComment != null) {
            body.add(bufferedComment)
        }

        return Resource(body)
    }

    private fun parseComment(): Entry {
        // Count leading hashes without consuming them
        val hashCount = countHashesAt(pos)

        if (hashCount == 3) {
            // Resource comment (###) - advance past ###, skip one space, read line
            pos += 3
            if (peek() == ' ') pos++ // skip single space after ###
            return Entry.ResourceComment(parseSingleCommentLine())
        }

        // For # and ##, advance past the hashes
        if (hashCount > 0) {
            pos += hashCount
        }

        val lines = mutableListOf<String>()

        // Read first line of comment
        while (pos < source.length && (source[pos] == ' ' || source[pos] == '\t')) {
            pos++
        }
        if (pos < source.length && source[pos] != '\n' && source[pos] != '\r') {
            lines.add(parseCommentLine())
        }

        // Now check for subsequent lines
        // We need to check if the next line is a blank line (end of comment block)
        // or if it's a continuation (same number of hashes)
        while (pos < source.length) {
            // Skip any newlines to get to the start of the next line
            while (pos < source.length && (source[pos] == '\n' || source[pos] == '\r')) {
                pos++
            }

            if (pos >= source.length) break

            // Check for blank line (two or more consecutive newlines)
            // After skipping initial newlines, check if we're at another newline
            if (source[pos] == '\n' || source[pos] == '\r') {
                break // blank line found
            }

            // Check if this is a continuation - must have same number of hashes
            val lineHashCount = countHashesAt(pos)
            if (lineHashCount != hashCount) {
                break // not a continuation
            }

            // It's a continuation - skip past the hashes and whitespace
            pos += hashCount
            while (pos < source.length && (source[pos] == ' ' || source[pos] == '\t')) {
                pos++
            }

            // Read the line content (may be empty)
            lines.add(parseCommentLine())
        }

        val content = lines.joinToString("\n")
        return when (hashCount) {
            2 -> Entry.GroupComment(content)
            else -> Entry.Comment(content)
        }
    }

    private fun countLeadingHashes(): Int {
        var count = 0
        while (pos < source.length && source[pos] == '#') {
            count++
            pos++
        }
        // Back up if we went too far (for hashCount == 3 check)
        if (count > 1 && pos < source.length) {
            // Check if this is actually ###
            if (count == 3 || (count == 2 && source[pos] != '#')) {
                // Valid # or ##, keep position
            } else if (count == 1 && source[pos] == '#') {
                // Actually ##, back up one
                pos--
            }
        }
        return if (count >= 3) 3 else count
    }

    private fun countHashesAt(pos: Int): Int {
        var count = 0
        var i = pos
        while (i < source.length && source[i] == '#') {
            count++
            i++
        }
        return count
    }

    private fun parseCommentLine(): String {
        val start = pos
        skipToNewline()
        return source.substring(start, pos).trim()
    }
    private fun parseSingleCommentLine(): String {
        val start = pos
        skipToNewline()
        return source.substring(start, pos).trim()
    }

    private fun bindDocCommentToMessage(message: Entry.Message, comment: Entry?): Entry.Message {
        when (comment) {
            is Entry.Comment -> {
                val docComment = parseDocComment(comment.content)
                return message.copy(docComment = docComment)
            }

            is Entry.GroupComment -> {
                val docComment = parseDocComment(comment.content)
                return message.copy(docComment = docComment)
            }

            else -> return message
        }
    }

    private fun bindDocCommentToTerm(term: Entry.Term, comment: Entry?): Entry.Term {
        when (comment) {
            is Entry.Comment -> {
                val docComment = parseDocComment(comment.content)
                return term.copy(docComment = docComment)
            }

            is Entry.GroupComment -> {
                val docComment = parseDocComment(comment.content)
                return term.copy(docComment = docComment)
            }

            else -> return term
        }
    }

    private fun parseDocComment(content: String): DocComment {
        val lines = content.lines()
        val variablesStart = lines.indexOfFirst { it.trim() == "Variables:" }
        if (variablesStart == -1) {
            return DocComment(description = content.trim())
        }
        val description = lines.subList(0, variablesStart).joinToString("\n").trim()
        val variables = parseVariableDocs(lines.subList(variablesStart + 1, lines.size))
        return DocComment(description = description, variables = variables)
    }

    private fun parseVariableDocs(lines: List<String>): List<VariableDoc> {
        val variables = mutableListOf<VariableDoc>()
        var current: VariableDoc? = null
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("$")) {
                // new variable line
                if (current != null) variables.add(current)
                current = parseSingleVariableDoc(trimmed)
            } else if (current != null && trimmed.isNotEmpty()) {
                // continuation line — append to current description
                current = current.copy(description = current.description + " " + trimmed)
            }
        }
        if (current != null) variables.add(current)
        return variables
    }

    private fun parseSingleVariableDoc(line: String): VariableDoc {
        // Remove leading $
        val text = line.removePrefix("$").trim()

        // Try: $name {type, "default"} - desc
        val braceMatch = Regex("""^(\w[\w-]*)\s*\{([^}]+)\}\s*[-:]\s*(.+)$""").matchEntire(text)
        if (braceMatch != null) {
            val (name, typeSpec, desc) = braceMatch.destructured
            val type = typeSpec.substringBefore(",").trim()
            val default = typeSpec.substringAfter(",").trim().removeSurrounding("\"")
            return VariableDoc(name, type, desc.trim(), default)
        }

        // Try: $name (Type): desc  OR  $name (Type) - desc
        val parenMatch = Regex("""^(\w[\w-]*)\s*\((\w+)\)\s*[:\-–]\s*(.+)$""").matchEntire(text)
        if (parenMatch != null) {
            val (name, type, desc) = parenMatch.destructured
            return VariableDoc(name, type, desc.trim())
        }

        // Try: $name : desc  OR  $name - desc  (no type)
        val noTypeMatch = Regex("""^(\w[\w-]*)\s*[:\-–]\s*(.+)$""").matchEntire(text)
        if (noTypeMatch != null) {
            val (name, desc) = noTypeMatch.destructured
            return VariableDoc(name, "", desc.trim())
        }

        // Fallback: treat whole line as name
        return VariableDoc(text, "")
    }

    private fun parseMessage(): Entry {
        val start = pos
        val id = parseIdentifier()

        skipWhitespace()

        if (peek() != '=') {
            errors.add(
                ParserError.Error(
                    ErrorKind.ExpectedToken('='),
                    "Expected '=' after message identifier",
                    Span(start, pos, source),
                ),
            )
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
                errors.add(
                    ParserError.Error(
                        ErrorKind.MissingField,
                        "Expected '=' after attribute identifier",
                        Span(pos, pos, source),
                    ),
                )
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
            errors.add(
                ParserError.Error(
                    ErrorKind.MissingField,
                    "Expected '=' after term identifier",
                    Span(start, pos, source),
                ),
            )
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

        // Track if we're at the start of a line (no elements yet parsed)
        var atStartOfContent = elements.isEmpty()

        while (pos < source.length && peek() != '\n') {
            // If we're at the start of content and see '.', this is an attribute marker - stop
            if (atStartOfContent && peek() == '.') {
                break
            }

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
                    atStartOfContent = false
                }

                else -> {
                    val textStart = pos
                    while (pos < source.length && peek() != '{' && peek() != '\n') {
                        pos++
                    }
                    val text = source.substring(textStart, pos)
                    if (text.isNotEmpty()) {
                        elements.add(PatternElement.TextElement(text))
                        atStartOfContent = false
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
                    if (i == 0 || line.isEmpty()) {
                        line
                    } else {
                        line.drop(minIndent)
                    }
                }.joinToString("\n")
                PatternElement.TextElement(dedented)
            } else {
                element
            }
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

        // Check for select expression variants: [key], *default, or -> syntax
        // Note: -> is checked here AND in parseVariants for standalone select
        skipWhitespace()
        if (peek() == '[' || peek() == '*' || (peek() == '-' && peekNext() == '>')) {
            // It's a select expression - parse variants
            val variants = parseVariants()
            return Expression.Select(inlineExpr, variants)
        }

        return Expression.Inline(inlineExpr)
    }

    private fun parseVariants(): List<Variant> {
        val variants = mutableListOf<Variant>()

        // Handle -> syntax: -> [key] value or -> *default value
        if (peek() == '-' && peekNext() == '>') {
            pos += 2 // skip ->
            skipWhitespace()
        }

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

            // Skip whitespace and newlines between variants
            skipWhitespace()
        }

        // Check for default variant - if none found, report error
        if (variants.isNotEmpty() && variants.none { it.default }) {
            errors.add(
                ParserError.Error(
                    ErrorKind.MissingDefaultVariant,
                    "The select expression must have a default variant",
                    Span(0, 0, source),
                ),
            )
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
                    if (peek() == '\\') {
                        pos++ // skip escape
                        if (pos < source.length) pos++ // skip escaped char
                    } else {
                        pos++
                    }
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

    private fun parseInlineExpression(): InlineExpression = when {
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

        peek() == '{' -> {
            // Nested placeable - { expr }
            pos++ // skip {
            skipWhitespace()
            val expr = parseExpression()
            skipWhitespace()
            if (peek() == '}') pos++
            InlineExpression.Placeable(expr)
        }

        else -> {
            val start = pos
            skipToNewline()
            InlineExpression.StringLiteral(source.substring(start, pos))
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
        } else {
            null
        }

        // Check for function arguments
        skipWhitespace()
        val arguments = if (peek() == '(') {
            parseCallArguments()
        } else {
            null
        }

        return if (arguments != null) {
            // Has arguments - could be term call or function call
            // If it started with -, it's a term call; otherwise it's a function call
            // We already skipped the -, so it's a term call
            // Preserve the attribute even when there are arguments
            InlineExpression.TermReference(Identifier(name), attribute, arguments)
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
        } else {
            null
        }

        // Check for function arguments
        skipWhitespace()
        val arguments = if (peek() == '(') {
            parseCallArguments()
        } else {
            null
        }

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
        var unterminated = false
        while (pos < source.length && peek() != '"') {
            if (peek() == '\\') {
                pos++ // skip escape
                if (pos < source.length) {
                    val ch = peek()
                    // Check for valid escape sequences
                    if (ch !in "u n r t \\ \" { } $") {
                        errors.add(
                            ParserError.Error(
                                ErrorKind.UnknownEscapeSequence,
                                "Unknown escape sequence: \\$ch",
                                Span(start, pos, source),
                            ),
                        )
                    }
                    pos++ // skip escaped char
                }
            } else {
                pos++
            }
        }
        if (pos >= source.length) {
            unterminated = true
        }
        val value = source.substring(start, pos)
        if (!unterminated && peek() == '"') pos++ // skip closing "
        if (unterminated) {
            errors.add(
                ParserError.Error(
                    ErrorKind.UnterminatedStringLiteral,
                    "Unterminated string literal",
                    Span(start, pos, source),
                ),
            )
        }
        return InlineExpression.StringLiteral(value)
    }

    private fun parseNumberLiteral(): InlineExpression.NumberLiteral {
        val start = pos
        if (peek() == '-') pos++
        while (pos < source.length &&
            (peek().isDigit() || peek() == '.' || peek() == 'e' || peek() == 'E' || peek() == '+' || peek() == '-')
        ) {
            pos++
        }
        return InlineExpression.NumberLiteral(source.substring(start, pos))
    }

    private fun parseCallArguments(): CallArguments {
        pos++ // skip (

        val positional = mutableListOf<InlineExpression>()
        val named = mutableListOf<NamedArgument>()
        var seenNamed = false

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
                    seenNamed = true
                } else {
                    // Positional argument - check if named args came before
                    if (seenNamed) {
                        errors.add(
                            ParserError.Error(
                                ErrorKind.PositionalArgumentFollowsNamed,
                                "Positional arguments must come before named arguments",
                                Span(pos - name.length, pos, source),
                            ),
                        )
                    }
                    // Positional argument - need to check if it's a function call
                    skipWhitespace()
                    when (peek()) {
                        '(' -> {
                            // Function call - parse the call arguments
                            val args = parseCallArguments()
                            positional.add(InlineExpression.FunctionReference(Identifier(name), args))
                        }

                        '.' -> {
                            // Attribute access
                            pos++ // skip .
                            skipWhitespace()
                            val attr = Identifier(parseIdentifier())
                            positional.add(InlineExpression.MessageReference(Identifier(name), attr))
                        }

                        else -> {
                            // Plain message reference
                            positional.add(InlineExpression.MessageReference(Identifier(name), null))
                        }
                    }
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
        return Entry.Comment(line)
    }

    private fun skipWhitespace() {
        while (pos < source.length &&
            (source[pos] == ' ' || source[pos] == '\t' || source[pos] == '\n' || source[pos] == '\r')
        ) {
            pos++
        }
    }

    private fun skipToNewline() {
        while (pos < source.length && source[pos] != '\n') {
            pos++
        }
    }

    private fun peek(): Char = source.getOrNull(pos) ?: '\u0000'

    private fun peekNext(): Char = source.getOrNull(pos + 1) ?: '\u0000'
    private fun isIdentifierStart(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c == '_'

    private fun isIdentifierPart(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '-'

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
    // Basic errors
    data object MissingField : ErrorKind()
    data object InvalidIdentifier : ErrorKind()
    data object InvalidToken : ErrorKind()
    data object UnexpectedToken : ErrorKind()

    // Token errors
    data class ExpectedToken(val token: Char) : ErrorKind()
    data class ExpectedCharRange(val range: String) : ErrorKind()

    // Field errors
    data class ExpectedMessageField(val entryId: String) : ErrorKind()
    data class ExpectedTermField(val entryId: String) : ErrorKind()

    // Select expression errors
    data object ForbiddenCallee : ErrorKind()
    data object MissingDefaultVariant : ErrorKind()
    data object MissingValue : ErrorKind()
    data object MultipleDefaultVariants : ErrorKind()

    // Selector errors
    data object MessageReferenceAsSelector : ErrorKind()
    data object TermReferenceAsSelector : ErrorKind()
    data object MessageAttributeAsSelector : ErrorKind()
    data object TermAttributeAsPlaceable : ErrorKind()

    // String literal errors
    data object UnterminatedStringLiteral : ErrorKind()
    data object UnknownEscapeSequence : ErrorKind()
    data class InvalidUnicodeEscapeSequence(val sequence: String) : ErrorKind()

    // Argument errors
    data object PositionalArgumentFollowsNamed : ErrorKind()
    data class DuplicatedNamedArgument(val name: String) : ErrorKind()

    // Expression errors
    data object UnbalancedClosingBrace : ErrorKind()
    data object ExpectedInlineExpression : ErrorKind()
    data object ExpectedSimpleExpressionAsSelector : ErrorKind()
    data object ExpectedLiteral : ErrorKind()

    // Display name for error messages
    override fun toString(): String = when (this) {
        is MissingField -> "Missing field"
        is InvalidIdentifier -> "Invalid identifier"
        is InvalidToken -> "Invalid token"
        is UnexpectedToken -> "Unexpected token"
        is ExpectedToken -> "Expected token '$token'"
        is ExpectedCharRange -> "Expected one of \"$range\""
        is ExpectedMessageField -> "Expected message field for '$entryId'"
        is ExpectedTermField -> "Expected term field for '$entryId'"
        is ForbiddenCallee -> "Callee is not allowed here"
        is MissingDefaultVariant -> "The select expression must have a default variant"
        is MissingValue -> "Expected a value"
        is MultipleDefaultVariants -> "A select expression can only have one default variant"
        is MessageReferenceAsSelector -> "Message references can't be used as a selector"
        is TermReferenceAsSelector -> "Term references can't be used as a selector"
        is MessageAttributeAsSelector -> "Message attributes can't be used as a selector"
        is TermAttributeAsPlaceable -> "Term attributes can't be used as a placeable"
        is UnterminatedStringLiteral -> "Unterminated string literal"
        is UnknownEscapeSequence -> "Unknown escape sequence"
        is InvalidUnicodeEscapeSequence -> "Invalid unicode escape sequence: $sequence"
        is PositionalArgumentFollowsNamed -> "Positional arguments must come before named arguments"
        is DuplicatedNamedArgument -> "The '$name' argument appears twice"
        is UnbalancedClosingBrace -> "Unbalanced closing brace"
        is ExpectedInlineExpression -> "Expected an inline expression"
        is ExpectedSimpleExpressionAsSelector -> "Expected a simple expression as selector"
        is ExpectedLiteral -> "Expected a string or number literal"
    }
}

data class Span(val start: Int, val end: Int, val sourceText: String) {
    /**
     * Get line number (1-indexed) for start position.
     */
    fun line(): Int {
        var line = 1
        for (i in 0 until minOf(start, sourceText.length)) {
            if (sourceText[i] == '\n') line++
        }
        return line
    }

    /**
     * Get column number (1-indexed) for start position.
     */
    fun column(): Int {
        var col = 1
        for (i in 0 until minOf(start, sourceText.length)) {
            if (sourceText[i] == '\n') {
                col = 1
            } else {
                col++
            }
        }
        return col
    }

    /**
     * Get a user-friendly display string.
     */
    override fun toString(): String = "${line()}:${column()}"
}
