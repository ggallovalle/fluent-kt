package dev.kbroom.fluent.codegen

private val KOTLIN_KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
    "if", "in", "interface", "is", "null", "object", "package", "return",
    "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
    "var", "when", "while", "by", "catch", "constructor", "delegate", "dynamic",
    "field", "file", "finally", "get", "import", "init", "param", "property",
    "receiver", "set", "setparam", "where", "actual", "abstract", "annotation",
    "companion", "const", "crossinline", "data", "enum", "expect", "external",
    "final", "infix", "inline", "inner", "internal", "lateinit", "noinline",
    "open", "operator", "out", "override", "private", "protected", "public",
    "reified", "sealed", "suspend", "tailrec", "value", "vararg",
)

/**
 * Naming helpers for generated Kotlin identifiers.
 */
object KotlinNames {
    fun toPascalCase(raw: String): String =
        splitParts(raw).joinToString("") { part ->
            part.replaceFirstChar { it.uppercaseChar() }
        }.ifEmpty { "X" }.let { escapeIfKeyword(it) }

    fun toCamelCase(raw: String): String {
        val parts = splitParts(raw)
        if (parts.isEmpty()) return escapeIfKeyword("x")
        val first = parts.first().replaceFirstChar { it.lowercaseChar() }
        val rest = parts.drop(1).joinToString("") { part ->
            part.replaceFirstChar { it.uppercaseChar() }
        }
        return escapeIfKeyword(first + rest)
    }

    fun toConstCase(raw: String): String =
        splitParts(raw).joinToString("_") { it.uppercaseCharSequence() }
            .ifEmpty { "X" }
            .let { escapeIfKeyword(it) }

    fun attributeMethodName(messageId: String, attribute: String): String =
        toCamelCase(messageId) + toPascalCase(attribute)

    fun kotlinType(typeHint: String): String = when (typeHint.trim().lowercase()) {
        "string" -> "String"
        "number", "int", "integer" -> "Number"
        "long" -> "Long"
        "double", "float" -> "Double"
        "bool", "boolean" -> "Boolean"
        else -> "Any?"
    }

    fun uniqueCamel(base: String, used: MutableSet<String>): String {
        val camel = toCamelCase(base)
        if (used.add(camel)) return camel
        var i = 2
        while (!used.add("$camel$i")) {
            i++
        }
        return "$camel$i"
    }

    private fun splitParts(raw: String): List<String> =
        raw.split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotEmpty() }
            .flatMap { camelSplit(it) }

    private fun camelSplit(token: String): List<String> {
        if (token.isEmpty()) return emptyList()
        val parts = mutableListOf<String>()
        val buf = StringBuilder()
        for (ch in token) {
            if (ch.isUpperCase() && buf.isNotEmpty() && buf.last().isLowerCase()) {
                parts += buf.toString()
                buf.clear()
            }
            buf.append(ch)
        }
        if (buf.isNotEmpty()) parts += buf.toString()
        return parts.map { it.lowercase() }
    }

    private fun String.uppercaseCharSequence(): String = uppercase()

    private fun escapeIfKeyword(name: String): String =
        if (name in KOTLIN_KEYWORDS) "`$name`" else name
}
