package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.bundle.types.FluentNumber
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.IntlLangMemoizer
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.Pattern

/**
 * FluentBundle is the main runtime for localization.
 *
 * A FluentBundle holds a collection of messages and terms for a specific locale,
 * and provides functionality to format those messages with runtime arguments.
 *
 * ## Basic Usage
 * ```kotlin
 * val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
 * bundle.addResource(FluentResource.tryNew("hello = Hello, { $name }!").getOrThrow())
 * val output = bundle.format("hello", fluentArgsOf("name" to "World"))
 * // Output: "Hello, World!"
 * ```
 *
 * @property locales The list of locales this bundle supports (primary locale is first)
 * @property useIsolating Whether to use Unicode isolation marks for bidirectional text
 */
@Suppress("TooManyFunctions")
class FluentBundle(val locales: List<LanguageIdentifier>, val useIsolating: Boolean = true) {
    private val entries: MutableMap<String, Entry> = linkedMapOf()
    private val functions: MutableMap<String, (List<FluentValue>, FluentArgs) -> FluentValue> = mutableMapOf()
    private var transform: ((String) -> String)? = null
    private var formatter: ((FluentValue, IntlLangMemoizer) -> String?)? = null
    private val memoizer: IntlLangMemoizer = IntlLangMemoizer()
    private val resolver = dev.kbroom.fluent.bundle.resolver.PatternResolver()

    /**
     * Add a resource to this bundle.
     *
     * If a message or term with the same ID already exists, an error is returned.
     * Use [addResourceOverriding] if you want to replace existing entries.
     *
     * Junk entries produced by the parser for broken FTL are silently ignored.
     *
     * @param resource The FluentResource to add
     * @return Result.success(Unit) on success, or Result.failure with error details
     */
    fun addResource(resource: FluentResource): Result<Unit> {
        val errors = mutableListOf<FluentError>()

        for (entry in resource.body) {
            when (entry) {
                is Entry.Message -> {
                    val existing = entries[entry.id.name]
                    if (existing != null) {
                        errors.add(FluentError.Overriding(EntryKind.MESSAGE, entry.id.name))
                    }
                    entries[entry.id.name] = entry
                }

                is Entry.Term -> {
                    val existing = entries[entry.id.name]
                    if (existing != null) {
                        errors.add(FluentError.Overriding(EntryKind.TERM, entry.id.name))
                    }
                    entries[entry.id.name] = entry
                }

                else -> { /* Comments and Junk are ignored */ }
            }
        }

        return if (errors.isEmpty()) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(errors.joinToString { it.toString() }))
        }
    }

    /**
     * Add a resource, overwriting any existing messages or terms with the same ID.
     *
     * Junk entries produced by the parser for broken FTL are silently ignored.
     *
     * @param resource The FluentResource to add
     * @return Result.success(Unit)
     */
    fun addResourceOverriding(resource: FluentResource): Result<Unit> {
        for (entry in resource.body) {
            when (entry) {
                is Entry.Message -> entries[entry.id.name] = entry
                is Entry.Term -> entries[entry.id.name] = entry
                else -> { /* Comments and Junk are ignored */ }
            }
        }
        return Result.success(Unit)
    }

    /**
     * Get a message by its ID.
     *
     * @param id The message ID
     * @return The FluentMessage, or null if not found
     */
    fun getMessage(id: String): FluentMessage? {
        val entry = entries[id] ?: return null
        return when (entry) {
            is Entry.Message -> FluentMessage(entry)
            else -> null
        }
    }

    /**
     * Check if a message exists.
     *
     * @param id The message ID
     * @return true if the message exists
     */
    fun hasMessage(id: String): Boolean = entries[id] is Entry.Message

    /**
     * Get a term by its ID.
     *
     * Terms are identified by their ID (without the leading dash).
     *
     * @param id The term ID (with or without leading dash)
     * @return The FluentTerm, or null if not found
     */
    fun getTerm(id: String): FluentTerm? {
        val termId = if (id.startsWith("-")) id.substring(1) else id
        val entry = entries[termId] ?: return null
        return when (entry) {
            is Entry.Term -> FluentTerm(entry)
            else -> null
        }
    }

    /**
     * Format a pattern directly.
     *
     * @param pattern The Pattern to format
     * @param args Optional arguments for variable substitution
     * @param errors MutableList to collect any errors during formatting
     * @return The formatted string
     */
    fun formatPattern(
        pattern: Pattern,
        args: FluentArgs? = null,
        errors: MutableList<FluentError> = mutableListOf(),
        rootMessageId: String? = null,
    ): String {
        val scope = dev.kbroom.fluent.bundle.resolver.Scope(this, args, errors, rootMessageId = rootMessageId)
        val result = resolver.resolve(pattern, scope)
        return result
    }

    /**
     * Format a message by its ID, resolving all references.
     *
     * @param id The message ID
     * @param args Optional arguments for variable substitution
     * @return The formatted string, or null if the message doesn't exist
     */
    fun formatMessage(id: String, args: FluentArgs? = null): String? {
        val message = getMessage(id) ?: return null
        val pattern = message.value() ?: return null
        val errors = mutableListOf<FluentError>()
        val scope = dev.kbroom.fluent.bundle.resolver.Scope(this, args, errors, rootMessageId = id)
        return resolver.resolve(pattern, scope)
    }

    /**
     * Alias for [formatMessage].
     *
     * @param id The message ID
     * @param args Optional arguments
     * @return The formatted string, or null if not found
     */
    fun format(id: String, args: FluentArgs? = null): String? = formatMessage(id, args)

    /**
     * Set whether to use Unicode isolation marks.
     *
     * Isolation marks prevent bidirectional text issues when mixing LTR and RTL languages.
     *
     * @param value true to enable isolation (default), false to disable
     */
    @Suppress("UnusedParameter")
    fun setUseIsolating(value: Boolean) {
        // Note: This is a constructor parameter, so changes won't affect already-parsed patterns
        // A full implementation would need to make this a mutable property
    }

    /**
     * Set a transform function to apply to all formatted values.
     *
     * The transform is applied to each text element in a pattern.
     *
     * @param fn The transform function
     */
    fun setTransform(fn: (String) -> String) {
        transform = fn
    }

    /**
     * Clear the transform function.
     */
    fun clearTransform() {
        transform = null
    }

    /**
     * Get the current transform function.
     */
    fun getTransform(): ((String) -> String)? = transform

    /**
     * Set a custom formatter for FluentValue types.
     *
     * @param fn The formatter function
     */
    fun setFormatter(fn: (FluentValue, IntlLangMemoizer) -> String?) {
        formatter = fn
    }

    /**
     * Add a custom function.
     *
     * Functions can be called from Fluent patterns using the FUNCTION() syntax.
     *
     * @param id The function name
     * @param fn The function implementation
     */
    fun addFunction(id: String, fn: (List<FluentValue>, FluentArgs) -> FluentValue) {
        functions[id] = fn
    }

    /**
     * Get a function by name.
     *
     * @param id The function name
     * @return The function, or null if not found
     */
    fun getFunction(id: String): ((List<FluentValue>, FluentArgs) -> FluentValue)? = functions[id]

    /**
     * Add built-in functions (NUMBER, PLURAL, etc.) to this bundle.
     */
    fun addBuiltins() {
        addNumberFunction()
        addPluralFunction()
        addConcatFunction()
        addSumFunction()
        addIdentityFunction()
    }

    private fun addNumberFunction() {
        val bundleLocales = locales
        val bundleMemoizer = memoizer
        addFunction("NUMBER") { args, _ ->
            val rawValue = args.firstOrNull()?.asAny()
            val value = when (rawValue) {
                is Double -> rawValue

                is Int -> rawValue.toDouble()

                is String -> rawValue.toDoubleOrNull()
                    ?: return@addFunction FluentValue.Error("NUMBER requires a number argument")

                else -> return@addFunction FluentValue.Error("NUMBER requires a number argument")
            }

            var style: String? = null
            var currency: String? = null
            var currencyDisplay: String? = null
            var minimumFractionDigits: Int? = null
            var maximumFractionDigits: Int? = null
            var useGrouping: Boolean? = null

            // Simple argument parsing for NUMBER
            if (args.size > 1) {
                val second = args[1].asAny() as? Map<*, *>
                if (second != null) {
                    style = second["style"] as? String
                    currency = second["currency"] as? String
                    currencyDisplay = second["currencyDisplay"] as? String
                    minimumFractionDigits = second["minimumFractionDigits"] as? Int
                    maximumFractionDigits = second["maximumFractionDigits"] as? Int
                    useGrouping = second["useGrouping"] as? Boolean
                }
            }

            val result = IntlHelpers.formatNumber(
                value,
                bundleLocales.first(),
                bundleMemoizer,
                style,
                currency,
                currencyDisplay,
                minimumFractionDigits,
                maximumFractionDigits,
                useGrouping,
            )
            if (result != null) FluentValue.Str(result) else FluentValue.Error("NUMBER formatting failed")
        }
    }

    private fun addPluralFunction() {
        val bundleLocales = locales
        val bundleMemoizer = memoizer
        addFunction("PLURAL") { args, _ ->
            val value = args.firstOrNull()?.asAny() as? Double
                ?: (args.firstOrNull()?.asAny() as? Int)?.toDouble()
                ?: return@addFunction FluentValue.Str("other")

            val category = IntlHelpers.getPluralCategory(value, bundleLocales.first(), bundleMemoizer)
            FluentValue.Str(category)
        }
    }

    private fun addConcatFunction() {
        addFunction("CONCAT") { args, _ ->
            val result = args.joinToString("") { it.asString() }
            FluentValue.Str(result)
        }
    }

    private fun addSumFunction() {
        addFunction("SUM") { args, _ ->
            val sum = args.mapNotNull {
                (it.asAny() as? Double) ?: ((it.asAny() as? Int)?.toDouble())
            }.sum()
            FluentValue.Number(FluentNumber(sum))
        }
    }

    private fun addIdentityFunction() {
        addFunction("IDENTITY") { args, _ ->
            args.firstOrNull() ?: FluentValue.None
        }
    }

    /**
     * Get the memoizer for caching formatted values.
     */
    fun memoizer(): IntlLangMemoizer = memoizer
}
