package dev.kbroom.fluent.testing

/**
 * A localized message value.
 */
data class L10nMessage(val value: String) {
    companion object {
        fun from(value: String?) = if (value != null) L10nMessage(value) else null
    }

    override fun toString(): String = value
}

/**
 * A localized attribute (key-value pair).
 */
data class L10nAttribute(val name: String, val value: String)

/**
 * A localization key (message id + optional attribute).
 */
data class L10nKey(val key: String, val attribute: String? = null) {
    fun toQueryId(): String = if (attribute != null) "$key.$attribute" else key

    override fun toString(): String = toQueryId()
}

/**
 * Exceptional context for queries - used to mark expected error conditions.
 */
enum class ExceptionalContext {
    None,
    OptionalResourceMissingFromLocale,
    RequiredResourceMissing,
    Error,
}

/**
 * A query to test against a bundle.
 */
data class L10nQuery(
    val key: String,
    val args: Map<String, Any?>? = null,
    val expected: String?,
    val context: ExceptionalContext = ExceptionalContext.None,
)

/**
 * Helper to create queries.
 */
fun query(key: String, expected: String, context: ExceptionalContext = ExceptionalContext.None) =
    L10nQuery(key, null, expected, context)

fun query(
    key: String,
    args: Map<String, Any?>,
    expected: String,
    context: ExceptionalContext = ExceptionalContext.None,
) = L10nQuery(key, args, expected, context)
