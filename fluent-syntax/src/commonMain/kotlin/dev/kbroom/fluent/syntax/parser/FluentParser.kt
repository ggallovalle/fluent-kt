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
 *
 * The class has 38 functions, 1039 lines, and several intentionally
 * branchy methods (parseComment, parsePattern, parseVariantKey). The
 * @Suppress annotations below match the bisne precedent: a faithful port
 * of the upstream parser, structured around the Fluent grammar, where
 * extracting the per-shape helpers would lose the locality that makes
 * the grammar easy to audit. New public entry points should land here
 * only when they correspond to a new grammar production.
 */
@Suppress(
    "TooManyFunctions",
    "LargeClass",
    "CyclomaticComplexMethod",
    "LoopWithTooManyJumpStatements",
)
class FluentParser {

    private var pos = 0
    private var source = ""
    private val errors = mutableListOf<ParserError>()

    /**
     * Set when a malformed placeable (e.g. `{1x}`) is encountered — its
     * expression parse consumed characters that should have remained inside
     * the placeable. The enclosing message becomes a Junk entry.
     */
    private var hasInvalidPlaceable = false

    fun parse(source: String): Resource {
        this.source = source
        this.pos = 0
        this.errors.clear()
        this.hasInvalidPlaceable = false

        val body = mutableListOf<Entry>()
        var bufferedComment: Entry? = null

        while (pos < source.length) {
            if (consumeBlankLine()) {
                bufferedComment?.let(body::add)
                bufferedComment = null
                pos++ // advance past the newline
                continue
            }
            skipWhitespace()
            if (isAtNewline()) {
                bufferedComment?.let(body::add)
                bufferedComment = null
                pos++ // advance past the newline
                continue
            }
            if (pos >= source.length) break
            bufferedComment = parseTopLevelEntry(body, bufferedComment)
        }

        bufferedComment?.let(body::add)
        return Resource(body)
    }

    private fun consumeBlankLine(): Boolean = peek() == '\n' || peek() == '\r'

    private fun isAtNewline(): Boolean = peek() == '\n' || peek() == '\r'

    private fun parseTopLevelEntry(body: MutableList<Entry>, bufferedComment: Entry?): Entry? = when {
        peek() == '#' -> parseComment()

        peek() == '-' -> {
            val termEntry = parseTerm()
            if (bufferedComment != null) {
                body.add(bindDocCommentToTerm(termEntry, bufferedComment))
            } else {
                body.add(termEntry)
            }
            null
        }

        isIdentifierStart(peek()) -> {
            val messageEntry = parseMessage()
            if (messageEntry is Entry.Message) {
                body.add(bindDocCommentToMessage(messageEntry, bufferedComment))
            } else {
                // A buffered doc comment only attaches to a valid message or
                // term. If the entry became Junk, emit the comment as a
                // standalone entry first.
                if (bufferedComment != null) {
                    body.add(bufferedComment)
                }
                body.add(messageEntry)
            }
            null
        }

        else -> {
            // Unrecognized content at the top level. Emit the buffered
            // comment (if any) as standalone, then collect a Junk entry
            // spanning this line and any subsequent junk lines.
            bufferedComment?.let(body::add)
            body.add(parseJunkLine(pos))
            null
        }
    }

    /**
     * Parse a single junk line plus continuation junk lines, returning a
     * [Entry.Junk] whose content captures the consumed span. Junk lines are
     * any lines that are not comments, blank, or valid message/term entries.
     */
    private fun parseJunkLine(start: Int): Entry.Junk {
        val lineStart = start
        var contentEnd = lineStart
        while (contentEnd < source.length && source[contentEnd] != '\n' && source[contentEnd] != '\r') {
            contentEnd++
        }
        // Include the line's terminator in junk content
        if (contentEnd < source.length && source[contentEnd] == '\r') contentEnd++
        if (contentEnd < source.length && source[contentEnd] == '\n') contentEnd++
        // Continue with subsequent junk lines (terminator already included)
        while (contentEnd < source.length) {
            var next = contentEnd
            while (next < source.length && (source[next] == ' ' || source[next] == '\t')) {
                next++
            }
            if (next >= source.length) break
            val first = source[next]
            // Comments, blank lines, valid entries terminate junk
            if (first == '#') break
            if (first == '\n' || first == '\r') {
                if (next < source.length && source[next] == '\r') contentEnd = next + 1 else contentEnd = next
                if (contentEnd < source.length && source[contentEnd] == '\n') contentEnd++
                break
            }
            if (first == '-' || isIdentifierStart(first)) break
            // Junk line — consume it
            while (contentEnd < source.length && source[contentEnd] != '\n' && source[contentEnd] != '\r') {
                contentEnd++
            }
            if (contentEnd < source.length && source[contentEnd] == '\r') contentEnd++
            if (contentEnd < source.length && source[contentEnd] == '\n') contentEnd++
        }
        pos = contentEnd
        val content = source.substring(lineStart, pos)
        errors.add(
            ParserError.Error(
                ErrorKind.InvalidToken,
                "Invalid token",
                Span(lineStart, pos, source),
            ),
        )
        return Entry.Junk(content)
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
            // Skip ONE line terminator.
            if (pos < source.length && source[pos] == '\r') pos++
            if (pos < source.length && source[pos] == '\n') pos++

            if (pos >= source.length) break

            // Check for blank line: a blank line is one or more newlines
            // immediately after the line terminator of the previous line.
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

        hasInvalidPlaceable = false
        val value = parseMessageValue()
        val attributes = parseMessageAttributes()
        val comment = parseInlineComment()

        if (isMessageBroken(value, attributes)) {
            return junkFromLine(start)
        }

        return Entry.Message(Identifier(id), value, attributes, comment)
    }

    /**
     * Parse the right-hand side of a `msg = ...` declaration. Returns `null`
     * if the value is missing (blank line, unindented new entry, or '.'
     * attribute follows). A multi-line pattern continuation is supported
     * when the next non-blank line is indented.
     */
    private fun parseMessageValue(): Pattern? {
        skipInlineWhitespace()
        if (peek() == '\n' || peek() == '\r') {
            // Possible multi-line continuation: scan past blank lines and any
            // indentation. Only treat as a continuation if the next non-blank
            // line is indented — a new entry on the next line is not a value.
            val startScan = pos
            // Skip one newline (blank line between declaration and value)
            if (peek() == '\r') pos++
            if (pos < source.length && peek() == '\n') pos++
            skipInlineWhitespace()
            val indented = pos > startScan + 1 &&
                (source[startScan + 1] == ' ' || source[startScan + 1] == '\t')
            val hasMore = pos < source.length
            return when {
                !hasMore -> {
                    pos = startScan
                    null
                }

                !indented -> {
                    // No value on this line and next line is unindented (or
                    // not present) — value is missing.
                    pos = startScan
                    null
                }

                peek() == '.' -> {
                    // Attribute follows — no value.
                    pos = startScan
                    null
                }

                else -> parsePattern()
            }
        }
        return if (pos < source.length) parsePattern() else null
    }

    /**
     * Parse attribute lines that belong to the current message. Each
     * `.attr = ...` block produces an [Attribute]; an attribute whose value
     * is missing or empty still occupies a slot in the list so the message
     * can be classified as broken if needed.
     */
    private fun parseMessageAttributes(): List<Attribute> {
        val attributes = mutableListOf<Attribute>()
        while (true) {
            skipWhitespace()
            if (peek() != '.') break
            pos++ // skip .
            skipInlineWhitespace()
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
            skipInlineWhitespace()
            val attrValue = parsePattern()
            attributes.add(Attribute(Identifier(attrId), attrValue))
        }
        return attributes
    }

    /**
     * A message is "broken" if any of:
     *  - it has no usable value and every attribute (when any are present)
     *    also has an empty value, or
     *  - it contained a malformed placeable (e.g. `{1x}` whose expression
     *    swallowed the closing `}`).
     *
     * Broken messages become Junk entries upstream — they cannot be
     * formatted usefully and they should not pollute the message catalog.
     */
    private fun isMessageBroken(value: Pattern?, attributes: List<Attribute>): Boolean {
        if (hasInvalidPlaceable) return true
        val valueIsEmpty = value == null || value.elements.isEmpty()
        if (!valueIsEmpty) return false
        if (attributes.isEmpty()) return true
        return attributes.all { it.value.elements.isEmpty() }
    }

    /**
     * Build a Junk entry that spans from [start] (the declaration site) through
     * every subsequent non-terminator line. Junk terminates at the first
     * `#` (comment), blank line, valid message/term identifier, or EOF.
     * A trailing blank line's terminator is included in the junk content so
     * round-trip serialization preserves the original spacing.
     */
    private fun junkFromLine(start: Int): Entry.Junk {
        // Walk back to the start of [start]'s line.
        var lineStart = start
        while (lineStart > 0 && source[lineStart - 1] != '\n' && source[lineStart - 1] != '\r') {
            lineStart--
        }
        // Walk forward to the end of the current line (excluding the newline).
        var contentEnd = lineStart
        while (contentEnd < source.length && source[contentEnd] != '\n' && source[contentEnd] != '\r') {
            contentEnd++
        }
        // Helper to advance over the line terminator and append the newline(s)
        // to content. Returns the new contentEnd position.
        fun consumeLineTerminator(): Int {
            var p = contentEnd
            if (p < source.length && source[p] == '\r') p++
            if (p < source.length && source[p] == '\n') p++
            return p
        }
        // Append the current line's newline
        contentEnd = consumeLineTerminator()

        // Scan forward through subsequent lines. Each non-terminator line is
        // appended to junk; blank lines append one extra newline and stop.
        while (contentEnd < source.length) {
            // Classify the next line.
            var next = contentEnd
            // Skip indentation (an indented line continues the previous
            // declaration's junk — covered by the upstream "subsequent junk
            // lines" rule).
            while (next < source.length && (source[next] == ' ' || source[next] == '\t')) {
                next++
            }
            if (next >= source.length) {
                // Trailing whitespace only — treat as terminator
                break
            }
            val first = source[next]
            // Comment line terminates junk (does not get included)
            if (first == '#') break
            // Blank line: include its newline in junk and stop
            if (first == '\n' || first == '\r') {
                contentEnd = consumeLineTerminator()
                break
            }
            // Valid message identifier or term dash starts a new entry; junk
            // ends before it.
            if (first == '-' || isIdentifierStart(first)) break
            // Otherwise it's a junk line: consume it, append newline.
            while (contentEnd < source.length && source[contentEnd] != '\n' && source[contentEnd] != '\r') {
                contentEnd++
            }
            contentEnd = consumeLineTerminator()
        }
        pos = contentEnd
        val content = source.substring(lineStart, pos)
        errors.add(
            ParserError.Error(
                ErrorKind.MissingValue,
                "Message has no value",
                Span(start, pos, source),
            ),
        )
        return Entry.Junk(content)
    }

    /**
     * Skip spaces and tabs only — not newlines. Used for inline whitespace
     * between tokens on a single line.
     */
    private fun skipInlineWhitespace() {
        while (pos < source.length && (source[pos] == ' ' || source[pos] == '\t')) {
            pos++
        }
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
                    skipInlineWhitespace()
                    if (peek() == '}') {
                        pos++ // skip }
                        continue
                    }
                    if (peek() == '\n' || peek() == '\r' || pos >= source.length) {
                        // Unterminated placeable at end of line — the
                        // parser cannot recover an expression here. Mark
                        // the message as broken so parseMessage emits Junk
                        // and lets the outer loop handle the next line
                        // (which is often a terminating `#` comment).
                        hasInvalidPlaceable = true
                        continue
                    }
                    val placeholderStart = pos
                    val expr = parseExpression()
                    skipWhitespace()
                    if (peek() == '}') {
                        pos++ // skip }
                    } else if (placeholderStart != pos) {
                        // The expression consumed characters but we never
                        // landed on the matching `}` — this is a malformed
                        // placeable (e.g. `{1x}`). Mark the enclosing
                        // message as broken so parseMessage turns it into
                        // Junk.
                        hasInvalidPlaceable = true
                        errors.add(
                            ParserError.Error(
                                ErrorKind.UnbalancedClosingBrace,
                                "Missing closing brace in placeable",
                                Span(placeholderStart - 1, pos, source),
                            ),
                        )
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
        if (isSelectExpressionStart()) {
            // It's a select expression - parse variants
            val variants = parseVariants()
            return Expression.Select(inlineExpr, variants)
        }

        return Expression.Inline(inlineExpr)
    }

    private fun isSelectExpressionStart(): Boolean {
        val ch = peek()
        return ch == '[' || ch == '*' || (ch == '-' && peekNext() == '>')
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
        while (pos < source.length && isNumberLiteralChar()) {
            pos++
        }
        return InlineExpression.NumberLiteral(source.substring(start, pos))
    }

    private fun isNumberLiteralChar(): Boolean = peek().let {
        it.isDigit() || it == '.' || it == 'e' || it == 'E' || it == '+' || it == '-'
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
        while (pos < source.length && isWhitespace(source[pos])) {
            pos++
        }
    }

    private fun isWhitespace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\r'

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

@Suppress("AbstractClassCanBeInterface")
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
    override fun toString(): String = displayName(this)
}

/**
 * Format an [ErrorKind] for human-readable error messages. Extracted from
 * the sealed class to keep its cyclomatic complexity within the detekt
 * threshold.
 */
@Suppress("CyclomaticComplexMethod")
private fun displayName(kind: ErrorKind): String = when (kind) {
    is ErrorKind.MissingField -> "Missing field"
    is ErrorKind.InvalidIdentifier -> "Invalid identifier"
    is ErrorKind.InvalidToken -> "Invalid token"
    is ErrorKind.UnexpectedToken -> "Unexpected token"
    is ErrorKind.ExpectedToken -> "Expected token '${kind.token}'"
    is ErrorKind.ExpectedCharRange -> "Expected one of \"${kind.range}\""
    is ErrorKind.ExpectedMessageField -> "Expected message field for '${kind.entryId}'"
    is ErrorKind.ExpectedTermField -> "Expected term field for '${kind.entryId}'"
    is ErrorKind.ForbiddenCallee -> "Callee is not allowed here"
    is ErrorKind.MissingDefaultVariant -> "The select expression must have a default variant"
    is ErrorKind.MissingValue -> "Expected a value"
    is ErrorKind.MultipleDefaultVariants -> "A select expression can only have one default variant"
    is ErrorKind.MessageReferenceAsSelector -> "Message references can't be used as a selector"
    is ErrorKind.TermReferenceAsSelector -> "Term references can't be used as a selector"
    is ErrorKind.MessageAttributeAsSelector -> "Message attributes can't be used as a selector"
    is ErrorKind.TermAttributeAsPlaceable -> "Term attributes can't be used as a placeable"
    is ErrorKind.UnterminatedStringLiteral -> "Unterminated string literal"
    is ErrorKind.UnknownEscapeSequence -> "Unknown escape sequence"
    is ErrorKind.InvalidUnicodeEscapeSequence -> "Invalid unicode escape sequence: ${kind.sequence}"
    is ErrorKind.PositionalArgumentFollowsNamed -> "Positional arguments must come before named arguments"
    is ErrorKind.DuplicatedNamedArgument -> "The '${kind.name}' argument appears twice"
    is ErrorKind.UnbalancedClosingBrace -> "Unbalanced closing brace"
    is ErrorKind.ExpectedInlineExpression -> "Expected an inline expression"
    is ErrorKind.ExpectedSimpleExpressionAsSelector -> "Expected a simple expression as selector"
    is ErrorKind.ExpectedLiteral -> "Expected a string or number literal"
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
