package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.Pattern
import dev.kbroom.fluent.syntax.PatternElement
import dev.kbroom.fluent.bundle.resolver.PatternResolver
import dev.kbroom.fluent.bundle.resolver.Scope
import dev.kbroom.fluent.bundle.types.FluentNumber
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.intl.IntlLangMemoizer

/**
 * FluentBundle is the main runtime for localization.
 */
class FluentBundle(
    val locales: List<LanguageIdentifier>,
    val useIsolating: Boolean = true,
) {
    private val entries: MutableMap<String, Entry> = linkedMapOf()
    private val functions: MutableMap<String, (List<FluentValue>, FluentArgs) -> FluentValue> = mutableMapOf()
    private var transform: ((String) -> String)? = null
    private var formatter: ((FluentValue, IntlLangMemoizer) -> String?)? = null
    private val memoizer: IntlLangMemoizer = IntlLangMemoizer()
    private val resolver = PatternResolver()
    
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
                else -> { /* Comments are ignored */ }
            }
        }
        
        return if (errors.isEmpty()) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(errors.joinToString { it.toString() }))
        }
    }
    
    fun addResourceOverriding(resource: FluentResource): Result<Unit> {
        for (entry in resource.body) {
            when (entry) {
                is Entry.Message -> entries[entry.id.name] = entry
                is Entry.Term -> entries[entry.id.name] = entry
                else -> { /* Comments are ignored */ }
            }
        }
        return Result.success(Unit)
    }
    
    fun getMessage(id: String): FluentMessage? {
        val entry = entries[id] ?: return null
        return when (entry) {
            is Entry.Message -> FluentMessage(entry)
            else -> null
        }
    }
    
    fun hasMessage(id: String): Boolean {
        val entry = entries[id]
        
        if (entry !is Entry.Message) return false
        
        val pattern = entry.value ?: return false
        
        val hasContent = pattern.elements.any { element ->
            when (element) {
                is PatternElement.TextElement -> {
                    val text = element.value.trim()
                    text.isNotEmpty() && !text.startsWith("#") && !text.startsWith("ERROR")
                }
                is PatternElement.Placeable -> true
            }
        }
        
        val hasAttrContent = entry.attributes.any { attr ->
            attr.value.elements.any { element ->
                when (element) {
                    is PatternElement.TextElement -> {
                        val text = element.value.trim()
                        text.isNotEmpty() && !text.startsWith("#") && !text.startsWith("ERROR")
                    }
                    is PatternElement.Placeable -> true
                }
            }
        }
        
        return hasContent || hasAttrContent
    }
    
    fun getTerm(id: String): FluentTerm? {
        val termId = if (id.startsWith("-")) id.substring(1) else id
        val entry = entries[termId] ?: return null
        return when (entry) {
            is Entry.Term -> FluentTerm(entry)
            else -> null
        }
    }
    
    fun formatPattern(
        pattern: Pattern,
        args: FluentArgs? = null,
        errors: MutableList<FluentError> = mutableListOf()
    ): String {
        val scope = Scope(this, args, errors)
        val result = resolver.resolve(pattern, scope)
        return result
    }
    
    /**
     * Format a message by its ID, resolving all references.
     */
    fun formatMessage(id: String, args: FluentArgs? = null): String? {
        val message = getMessage(id) ?: return null
        val pattern = message.value() ?: return null
        val errors = mutableListOf<FluentError>()
        val scope = Scope(this, args, errors)
        return resolver.resolve(pattern, scope)
    }
    fun format(id: String, args: FluentArgs? = null): String? {
        return formatMessage(id, args)
    }
    
    fun setUseIsolating(value: Boolean) {
        // Not implemented in simplified version
    }
    
    fun setTransform(fn: (String) -> String) {
        transform = fn
    }
    
    fun clearTransform() {
        transform = null
    }
    
    fun getTransform(): ((String) -> String)? = transform
    fun setFormatter(fn: (FluentValue, IntlLangMemoizer) -> String?) {
        formatter = fn
    }
    
    fun addFunction(id: String, fn: (List<FluentValue>, FluentArgs) -> FluentValue) {
        functions[id] = fn
    }
    
    fun getFunction(id: String): ((List<FluentValue>, FluentArgs) -> FluentValue)? {
        return functions[id]
    }
    
    fun addBuiltins() {
        val bundleLocales = locales
        val bundleMemoizer = memoizer
        
        addFunction("NUMBER") { args, _ ->
            if (args.isNotEmpty()) {
                val num = args[0]
                when (num) {
                    is FluentValue.Number -> {
                        val locale = bundleLocales.firstOrNull()
                        val formatted = if (locale != null) {
                            val numberOptions = num.value.options
                            IntlHelpers.formatNumber(
                                value = num.value.value,
                                locale = locale,
                                memoizer = bundleMemoizer,
                                style = numberOptions.style?.name?.lowercase(),
                                currency = numberOptions.currency,
                                currencyDisplay = numberOptions.currencyDisplay?.name?.lowercase(),
                                minimumFractionDigits = numberOptions.minimumFractionDigits,
                                maximumFractionDigits = numberOptions.maximumFractionDigits,
                                useGrouping = true
                            )
                        } else null
                        
                        if (formatted != null) {
                            FluentValue.Str(formatted)
                        } else {
                            val v = num.value.value
                            val intValue = v.toLong()
                            if (v == intValue.toDouble() && intValue.toDouble() == v) {
                                FluentValue.Str(intValue.toString())
                            } else {
                                FluentValue.Str(v.toString())
                            }
                        }
                    }
                    is FluentValue.Str -> {
                        val d = num.value.toDoubleOrNull() ?: 0.0
                        if (d == d.toLong().toDouble() && d.toLong().toDouble() == d) {
                            FluentValue.Str(d.toLong().toString())
                        } else {
                            FluentValue.Str(d.toString())
                        }
                    }
                    else -> FluentValue.Str(num.asString())
                }
            } else {
                FluentValue.Str("")
            }
        }
        
        addFunction("PLURAL") { args, _ ->
            if (args.isNotEmpty()) {
                val num = args[0]
                val value = when (num) {
                    is FluentValue.Number -> num.value.value
                    is FluentValue.Str -> num.value.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
                val locale = bundleLocales.firstOrNull() ?: LanguageIdentifier.parse("en")
                val category = IntlHelpers.getPluralCategory(value, locale, bundleMemoizer)
                FluentValue.Str(category)
            } else {
                FluentValue.Str("other")
            }
        }
        
        addFunction("DATETIME") { args, _ ->
            if (args.isNotEmpty()) {
                val value = args[0]
                val timestamp = when (value) {
                    is FluentValue.Number -> value.value.value.toLong()
                    is FluentValue.Str -> value.value.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val locale = bundleLocales.firstOrNull() ?: LanguageIdentifier.parse("en")
                
                val formatted = IntlHelpers.formatDateTime(
                    value = timestamp,
                    locale = locale,
                    memoizer = bundleMemoizer,
                    dateStyle = null,
                    timeStyle = null,
                    hour12 = null,
                    timeZone = null
                )
                FluentValue.Str(formatted ?: timestamp.toString())
            } else {
                FluentValue.Str("")
            }
        }
        
        addFunction("LIST") { args, _ ->
            if (args.isNotEmpty()) {
                val locale = bundleLocales.firstOrNull() ?: LanguageIdentifier.parse("en")
                val stringValues = args.map { it.asString() }
                val formatted = IntlHelpers.formatList(
                    values = stringValues,
                    locale = locale,
                    memoizer = bundleMemoizer,
                    type = "conjunction",
                    style = "long"
                )
                FluentValue.Str(formatted ?: stringValues.joinToString(", "))
            } else {
                FluentValue.Str("")
            }
        }
        
        addFunction("CONCAT") { args, _ ->
            val sb = StringBuilder()
            for (arg in args) {
                sb.append(arg.asString())
            }
            FluentValue.Str(sb.toString())
        }
        
        addFunction("SUM") { args, _ ->
            var sum = 0.0
            for (arg in args) {
                sum += when (arg) {
                    is FluentValue.Number -> arg.value.value
                    is FluentValue.Str -> arg.value.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
            }
            FluentValue.Number(FluentNumber(sum))
        }
        
        addFunction("IDENTITY") { args, _ ->
            args.firstOrNull() ?: FluentValue.None
        }
    }
    
    fun memoizer(): IntlLangMemoizer = memoizer
}
