package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.bundle.types.FluentNumber
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.IntlLangMemoizer
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.Pattern

// ---------------------------------------------------------------------------
// Internal type aliases
// ---------------------------------------------------------------------------

/** Signature for a function registered on a [FluentBundle]. */
internal typealias BuiltinFn = (List<FluentValue>, FluentArgs) -> FluentValue

/**
 * Immutable runtime bundle for localizing messages.
 *
 * A [FluentBundle] holds messages, terms, and functions for a specific locale,
 * plus the configuration needed to format them. Construct one through
 * [FluentBundleBuilder] (or the [fluentBundle] DSL) — the public constructor
 * is internal so bundles are guaranteed immutable by construction.
 *
 * ## Basic usage
 * ```kotlin
 * val bundle = fluentBundle(locales = listOf(LanguageIdentifier.parse("en-US"))) {
 *     resource("hello = Hello, { $name }!")
 *     builtins()
 * }
 * val output = bundle.format("hello", fluentArgsOf("name" to "World"))
 * ```
 *
 * After [build] returns, every method on this class is safe to call from
 * multiple threads simultaneously. The internal state is populated once and
 * never mutated.
 *
 * @property locales The list of locales this bundle supports (primary locale is first)
 * @property useIsolating Whether Unicode bidi isolation marks are emitted
 */
@Suppress("TooManyFunctions")
class FluentBundle internal constructor(private val builder: FluentBundleBuilder) {
    val locales: List<LanguageIdentifier> = builder.localesView
    val useIsolating: Boolean = builder.useIsolatingView

    private val entries: Map<String, Entry> = builder.entriesSnapshot
    private val functions: Map<String, BuiltinFn> = builder.functionsSnapshot
    private val transform: ((String) -> String)? = builder.transformView
    private val formatter: ((FluentValue, IntlLangMemoizer) -> String?)? = builder.formatterView
    private val memoizer: IntlLangMemoizer = builder.memoizerView
    private val resolver = dev.kbroom.fluent.bundle.resolver.PatternResolver()

    /**
     * Look up a message by its ID.
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
     */
    fun hasMessage(id: String): Boolean = entries[id] is Entry.Message

    /**
     * Check if a term exists. Accepts the term ID with or without the leading dash.
     */
    fun hasTerm(id: String): Boolean {
        val termId = if (id.startsWith("-")) id.substring(1) else id
        return entries[termId] is Entry.Term
    }

    /**
     * Check if a custom function is registered.
     */
    fun hasFunction(id: String): Boolean = functions.containsKey(id)

    /**
     * Look up any entry (message or term) by ID. Corresponds to
     * fluent-rs's `FluentBundle::get_entry`.
     */
    fun getEntry(id: String): Entry? {
        val termId = if (id.startsWith("-")) id.substring(1) else id
        return entries[termId]
    }

    /**
     * Snapshot copy of every message and term in this bundle, keyed by ID.
     * Matches fluent-rs's `FluentBundle::entries`.
     */
    fun entries(): Map<String, Entry> = entries

    /**
     * Look up a term by its ID.
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
     */
    fun formatPattern(
        pattern: Pattern,
        args: FluentArgs? = null,
        errors: MutableList<FluentError> = mutableListOf(),
        rootMessageId: String? = null,
    ): String {
        val scope = dev.kbroom.fluent.bundle.resolver.Scope(this, args, errors, rootMessageId = rootMessageId)
        return resolver.resolve(pattern, scope)
    }

    /**
     * Format a message by its ID, resolving all references.
     */
    fun formatMessage(id: String, args: FluentArgs? = null): String? {
        val message = getMessage(id) ?: return null
        val pattern = message.value() ?: return null
        val errors = mutableListOf<FluentError>()
        val scope = dev.kbroom.fluent.bundle.resolver.Scope(this, args, errors, rootMessageId = id)
        return resolver.resolve(pattern, scope)
    }

    /**
     * Format a specific attribute of a message by ID.
     *
     * Uses the AST path (calls [FluentMessage.getAttributeValue]) rather than
     * string-concatenating `"$id.$attribute"`. Returns null if either the
     * message or the attribute is missing.
     */
    fun formatAttribute(id: String, attribute: String, args: FluentArgs? = null): String? {
        val message = getMessage(id) ?: return null
        val pattern = message.getAttributeValue(attribute) ?: return null
        val errors = mutableListOf<FluentError>()
        val scope = dev.kbroom.fluent.bundle.resolver.Scope(this, args, errors, rootMessageId = "$id.$attribute")
        return resolver.resolve(pattern, scope)
    }

    /**
     * Alias for [formatMessage].
     */
    fun format(id: String, args: FluentArgs? = null): String? = formatMessage(id, args)

    /**
     * The transform function passed at build time, or null if none.
     */
    fun getTransform(): ((String) -> String)? = transform

    /**
     * Look up a custom function by name.
     */
    fun getFunction(id: String): ((List<FluentValue>, FluentArgs) -> FluentValue)? = functions[id]

    /**
     * Get the memoizer for caching formatted values.
     */
    fun memoizer(): IntlLangMemoizer = memoizer

    // Internal: read-only access for the resolver and the formatter hook.
    internal fun formatterFn(): ((FluentValue, IntlLangMemoizer) -> String?)? = formatter
}

/**
 * Builder for immutable [FluentBundle] instances.
 *
 * Use [FluentBundle.builder] or the [fluentBundle] DSL. The builder is single-use
 * — call [build] once to materialize the immutable bundle, then discard the
 * builder. Mutating methods are not exposed on [FluentBundle] itself; the
 * builder is the only way to populate one.
 *
 * ## Example
 * ```kotlin
 * val bundle = FluentBundle.builder(listOf(LanguageIdentifier.parse("en")))
 *     .addResource(FluentResource.tryNew("hi = Hello").getOrThrow())
 *     .addBuiltins()
 *     .setTransform { it.uppercase() }
 *     .build()
 * ```
 *
 * Or via the DSL:
 * ```kotlin
 * val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
 *     resource("hi = Hello")
 *     builtins()
 *     transform { it.uppercase() }
 * }
 * */
@Suppress("TooManyFunctions")
class FluentBundleBuilder internal constructor(
    private val locales: List<LanguageIdentifier>,
    private var useIsolating: Boolean = true,
) {
    private val entries: MutableMap<String, Entry> = linkedMapOf()
    private val functions: MutableMap<String, BuiltinFn> = linkedMapOf()
    private var transform: ((String) -> String)? = null
    private var formatter: ((FluentValue, IntlLangMemoizer) -> String?)? = null

    /**
     * Add a parsed [FluentResource]. If a message or term with the same ID
     * already exists, the call returns a failure listing the collisions
     * but does NOT throw — partial registration is preserved.
     *
     * To silently replace existing entries, use [addResourceOverriding].
     */
    fun addResource(resource: FluentResource): Result<FluentBundleBuilder> {
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
            Result.success(this)
        } else {
            Result.failure(Exception(errors.joinToString { it.toString() }))
        }
    }

    /**
     * Add a resource, overwriting any existing messages or terms with the same ID.
     */
    fun addResourceOverriding(resource: FluentResource): FluentBundleBuilder {
        for (entry in resource.body) {
            when (entry) {
                is Entry.Message -> entries[entry.id.name] = entry
                is Entry.Term -> entries[entry.id.name] = entry
                else -> { /* Comments and Junk are ignored */ }
            }
        }
        return this
    }

    /**
     * Add a custom function callable from FTL patterns.
     */
    fun addFunction(id: String, fn: (List<FluentValue>, FluentArgs) -> FluentValue): FluentBundleBuilder {
        functions[id] = fn
        return this
    }

    /**
     * Register the built-in functions (NUMBER, PLURAL, CONCAT, SUM, IDENTITY,
     * DATETIME, DATE, TIME, LIST) on this bundle.
     */
    fun addBuiltins(): FluentBundleBuilder {
        addNumberFunction()
        addPluralFunction()
        addConcatFunction()
        addSumFunction()
        addIdentityFunction()
        addDateTimeFunction()
        addDateFunction()
        addTimeFunction()
        addListFunction()
        return this
    }

    /**
     * Set the transform applied to each text element of every pattern. Useful
     * for pseudolocalization or text normalization.
     */
    fun setTransform(fn: (String) -> String): FluentBundleBuilder {
        transform = fn
        return this
    }

    /**
     * Clear any previously set transform.
     */
    fun clearTransform(): FluentBundleBuilder {
        transform = null
        return this
    }

    /**
     * Set a custom formatter for [FluentValue] types. Called by the resolver
     * when a non-string value needs to be rendered.
     */
    fun setFormatter(fn: (FluentValue, IntlLangMemoizer) -> String?): FluentBundleBuilder {
        formatter = fn
        return this
    }

    /**
     * Toggle Unicode bidi isolation marks (default on).
     */
    fun setUseIsolating(value: Boolean): FluentBundleBuilder {
        useIsolating = value
        return this
    }

    /**
     * Materialize the immutable bundle.
     */
    fun build(): FluentBundle = FluentBundle(this)

    // --- Snapshot accessors used by FluentBundle.build() ------------------------

    internal val localesView: List<LanguageIdentifier> get() = locales
    internal val useIsolatingView: Boolean get() = useIsolating
    internal val entriesSnapshot: Map<String, Entry> get() = entries.toMap()
    internal val functionsSnapshot: Map<String, BuiltinFn> get() = functions.toMap()
    internal val transformView: ((String) -> String)? get() = transform
    internal val formatterView: ((FluentValue, IntlLangMemoizer) -> String?)? get() = formatter
    internal val memoizerView: IntlLangMemoizer get() = IntlLangMemoizer()

    // --- Built-in function implementations --------------------------------------

    private fun addNumberFunction() {
        val bundleLocales = locales
        val bundleMemoizer = IntlLangMemoizer() // local — shared at format-time
        addFunction("NUMBER") { positional, named ->
            val rawValue = positional.firstOrNull()?.asAny()
            val value = when (rawValue) {
                is Double -> rawValue
                is Int -> rawValue.toDouble()
                is Long -> rawValue.toDouble()
                is Float -> rawValue.toDouble()
                is String -> rawValue.toDoubleOrNull()
                    ?: return@addFunction FluentValue.Error("NUMBER requires a number argument")
                else -> return@addFunction FluentValue.Error("NUMBER requires a number argument")
            }

            val style = optString(namedOrPositional(named, positional, "style", 1))
            val currency = optString(namedOrPositional(named, positional, "currency", 2))
            val currencyDisplay = optString(namedOrPositional(named, positional, "currencyDisplay", 3))
            val minimumFractionDigits = optInt(namedOrPositional(named, positional, "minimumFractionDigits", 4))
            val maximumFractionDigits = optInt(namedOrPositional(named, positional, "maximumFractionDigits", 5))
            val useGrouping = optBoolean(namedOrPositional(named, positional, "useGrouping", 6))

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
        val bundleMemoizer = IntlLangMemoizer()
        addFunction("PLURAL") { positional, named ->
            val value = positional.firstOrNull()?.asAny() as? Double
                ?: (positional.firstOrNull()?.asAny() as? Int)?.toDouble()
                ?: return@addFunction FluentValue.Str("other")

            val ordinal = optBoolean(namedOrPositional(named, positional, "ordinal", 1)) ?: false
            val category = if (ordinal) {
                IntlHelpers.getOrdinalPluralCategory(value, bundleLocales.first(), bundleMemoizer)
            } else {
                IntlHelpers.getPluralCategory(value, bundleLocales.first(), bundleMemoizer)
            }
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

    private fun addDateTimeFunction() {
        val bundleLocales = locales
        val bundleMemoizer = IntlLangMemoizer()
        addFunction("DATETIME") { positional, named ->
            val raw = positional.firstOrNull()?.asAny()
            val value = when (raw) {
                is Long -> raw
                is Int -> raw.toLong()
                is Double -> raw.toLong()
                is String -> raw.toLongOrNull()
                    ?: return@addFunction FluentValue.Error("DATETIME requires a number argument")
                else -> return@addFunction FluentValue.Error("DATETIME requires a number argument")
            }
            val dateStyle = optString(namedOrPositional(named, positional, "dateStyle", 1))
            val timeStyle = optString(namedOrPositional(named, positional, "timeStyle", 2))
            val hour12 = optBoolean(namedOrPositional(named, positional, "hour12", 3))
            val timeZone = optString(namedOrPositional(named, positional, "timeZone", 4))
            val result = IntlHelpers.formatDateTime(
                value,
                bundleLocales.first(),
                bundleMemoizer,
                dateStyle,
                timeStyle,
                hour12,
                timeZone,
            )
            if (result != null) FluentValue.Str(result) else FluentValue.Error("DATETIME formatting failed")
        }
    }

    private fun addDateFunction() {
        val bundleLocales = locales
        val bundleMemoizer = IntlLangMemoizer()
        addFunction("DATE") { positional, named ->
            val raw = positional.firstOrNull()?.asAny()
            val value = when (raw) {
                is Long -> raw
                is Int -> raw.toLong()
                is Double -> raw.toLong()
                is String -> raw.toLongOrNull()
                    ?: return@addFunction FluentValue.Error("DATE requires a number argument")
                else -> return@addFunction FluentValue.Error("DATE requires a number argument")
            }
            val style = optString(namedOrPositional(named, positional, "style", 1)) ?: "medium"
            val timeZone = optString(namedOrPositional(named, positional, "timeZone", 2))
            val result = IntlHelpers.formatDate(value, bundleLocales.first(), bundleMemoizer, style, timeZone)
            if (result != null) FluentValue.Str(result) else FluentValue.Error("DATE formatting failed")
        }
    }

    private fun addTimeFunction() {
        val bundleLocales = locales
        val bundleMemoizer = IntlLangMemoizer()
        addFunction("TIME") { positional, named ->
            val raw = positional.firstOrNull()?.asAny()
            val value = when (raw) {
                is Long -> raw
                is Int -> raw.toLong()
                is Double -> raw.toLong()
                is String -> raw.toLongOrNull()
                    ?: return@addFunction FluentValue.Error("TIME requires a number argument")
                else -> return@addFunction FluentValue.Error("TIME requires a number argument")
            }
            val style = optString(namedOrPositional(named, positional, "style", 1)) ?: "medium"
            val hour12 = optBoolean(namedOrPositional(named, positional, "hour12", 2))
            val timeZone = optString(namedOrPositional(named, positional, "timeZone", 3))
            val result = IntlHelpers.formatTime(value, bundleLocales.first(), bundleMemoizer, style, hour12, timeZone)
            if (result != null) FluentValue.Str(result) else FluentValue.Error("TIME formatting failed")
        }
    }

    private fun addListFunction() {
        val bundleLocales = locales
        val bundleMemoizer = IntlLangMemoizer()
        addFunction("LIST") { positional, named ->
            val values = positional.map { it.asString() }
            val type = optString(namedOrPositional(named, positional, "type", positional.size)) ?: "conjunction"
            val style = optString(namedOrPositional(named, positional, "style", positional.size + 1)) ?: "long"
            val result = IntlHelpers.formatList(values, bundleLocales.first(), bundleMemoizer, type, style)
            if (result != null) FluentValue.Str(result) else FluentValue.Error("LIST formatting failed")
        }
    }

    /**
     * Read a named option, falling back to positional[n] for fluent-rs-style
     * `FUNC($v, "long")` calls. Returns null if neither is present.
     */
    private fun namedOrPositional(
        named: FluentArgs,
        positional: List<FluentValue>,
        name: String,
        positionalIndex: Int,
    ): FluentValue? {
        named.get(name)?.let { if (it !is FluentValue.None) return it }
        return positional.getOrNull(positionalIndex)?.takeIf { it !is FluentValue.None }
    }

    private fun optString(v: FluentValue?): String? =
        v?.asString()?.takeIf { it.isNotEmpty() || v is FluentValue.Str }

    private fun optInt(v: FluentValue?): Int? = when (val av = v?.asAny()) {
        is Int -> av
        is Long -> av.toInt()
        is Double -> av.toInt()
        is Float -> av.toInt()
        is String -> av.toIntOrNull()
        else -> null
    }

    private fun optBoolean(v: FluentValue?): Boolean? = when (val av = v?.asAny()) {
        is Boolean -> av
        is String -> av.toBooleanStrictOrNull()
        else -> null
    }

    companion object {
        /**
         * Start a new builder for the given locales.
         */
        fun builder(locales: List<LanguageIdentifier>): FluentBundleBuilder =
            FluentBundleBuilder(locales)

        /**
         * Start a new builder for the given locales with `useIsolating` set.
         */
        fun builder(locales: List<LanguageIdentifier>, useIsolating: Boolean): FluentBundleBuilder =
            FluentBundleBuilder(locales, useIsolating)
    }
}

/**
 * DSL entry point for building an immutable [FluentBundle].
 *
 * ```kotlin
 * val bundle = fluentBundle(locales = listOf(LanguageIdentifier.parse("en-US"))) {
 *     resource("hello = Hello, { $name }!")
 *     resource(File("messages.ftl"))         // JVM only
 *     builtins()                              // registers NUMBER/PLURAL/DATETIME/...
 *     transform { it.uppercase() }
 *     isolating = false
 *     function("HELLO") { args, _ -> FluentValue.Str("Hi ${args.firstOrNull()?.asString()}") }
 * }
 * ```
 *
 * Throws [IllegalArgumentException] if any [resource] call fails to parse.
 *
 * @param locales Locales this bundle supports (primary locale first).
 * @param useIsolating Default Unicode bidi isolation behavior.
 * @param block Builder configuration.
 */
fun fluentBundle(
    locales: List<LanguageIdentifier>,
    useIsolating: Boolean = true,
    block: FluentBundleBuilder.() -> Unit = {},
): FluentBundle {
    val builder = FluentBundleBuilder(locales, useIsolating)
    builder.block()
    return builder.build()
}

// ---------------------------------------------------------------------------
// DSL extension functions
// ---------------------------------------------------------------------------

/**
 * Parse inline FTL source and add it to the bundle.
 *
 * Throws [IllegalArgumentException] if parsing fails. Use
 * [addResource] if you need a Result return type.
 */
fun FluentBundleBuilder.resource(source: String): FluentBundleBuilder {
    val parsed = FluentResource.tryNew(source).getOrElse {
        throw IllegalArgumentException("Failed to parse FTL resource", it)
    }
    return addResource(parsed).getOrElse {
        throw IllegalArgumentException("Resource conflicts: ${it.message}")
    }
}

/**
 * Alias matching the imperative [addResource] — same shape, DSL-friendly name.
 */
fun FluentBundleBuilder.resources(source: String): FluentBundleBuilder = resource(source)

/**
 * Register the built-in functions. DSL alias for [addBuiltins].
 */
fun FluentBundleBuilder.builtins(): FluentBundleBuilder = addBuiltins()

/**
 * Register a custom function callable from FTL patterns.
 *
 * ```kotlin
 * function("HELLO") { args, _ -> FluentValue.Str("Hi ${args.firstOrNull()?.asString()}") }
 * ```
 */
fun FluentBundleBuilder.function(
    id: String,
    fn: (List<FluentValue>, FluentArgs) -> FluentValue,
): FluentBundleBuilder = addFunction(id, fn)

/**
 * Set the transform applied to every formatted text element.
 */
fun FluentBundleBuilder.transform(fn: (String) -> String): FluentBundleBuilder = setTransform(fn)
