package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.syntax.*
import dev.kbroom.fluent.bundle.resolver.PatternResolver
import dev.kbroom.fluent.bundle.resolver.Scope
import dev.kbroom.fluent.bundle.types.FluentNumber
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.intl.IntlLangMemoizer
import dev.kbroom.fluent.bundle.types.getPluralCategory
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
        
        // Check if pattern has actual content (not just comments or whitespace)
        val hasContent = pattern.elements.any { element ->
            when (element) {
                is dev.kbroom.fluent.syntax.PatternElement.TextElement -> {
                    // Skip if it looks like a comment (starts with #)
                    val text = element.value.trim()
                    text.isNotEmpty() && !text.startsWith("#") && !text.startsWith("ERROR")
                }
                is dev.kbroom.fluent.syntax.PatternElement.Placeable -> true
            }
        }
        
        // Also check attributes
        val hasAttrContent = entry.attributes.any { attr ->
            attr.value.elements.any { element ->
                when (element) {
                    is dev.kbroom.fluent.syntax.PatternElement.TextElement -> {
                        val text = element.value.trim()
                        text.isNotEmpty() && !text.startsWith("#") && !text.startsWith("ERROR")
                    }
                    is dev.kbroom.fluent.syntax.PatternElement.Placeable -> true
                }
            }
        }
        
        return hasContent || hasAttrContent
    }
    
    fun getTerm(id: String): FluentTerm? {
        // Strip leading dash if present (term references use -term)
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
        return transform?.invoke(result) ?: result
    }
    
    fun format(id: String, args: FluentArgs? = null): String? {
        val message = getMessage(id) ?: return null
        val pattern = message.value() ?: return null
        val errors = mutableListOf<FluentError>()
        return formatPattern(pattern, args, errors)
    }
    
    fun setUseIsolating(value: Boolean) {
        // Not implemented in simplified version
    }
    
    fun setTransform(fn: (String) -> String) {
        transform = fn
    }
    
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
        addFunction("NUMBER") { args, _ ->
            if (args.isNotEmpty()) {
                val num = args[0]
                when (num) {
                    is FluentValue.Number -> FluentValue.Str(num.asString())
                    is FluentValue.Str -> {
                        val d = num.value.toDoubleOrNull() ?: 0.0
                        // Format as integer if whole number
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
                val locale = locales.firstOrNull()?.language ?: "en"
                val category = getPluralCategory(value, locale)
                FluentValue.Str(category.name.lowercase())
            } else {
                FluentValue.Str("other")
            }
        }
        
        // CONCAT function - concatenates string arguments
        addFunction("CONCAT") { args, _ ->
            val sb = StringBuilder()
            for (arg in args) {
                sb.append(arg.asString())
            }
            FluentValue.Str(sb.toString())
        }
        
        // SUM function - adds numbers
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
        
        // IDENTITY function - returns first argument, or name as fallback
        addFunction("IDENTITY") { args, _ ->
            args.firstOrNull() ?: FluentValue.Str("IDENTITY()")
        }
    }
    
    fun memoizer(): IntlLangMemoizer = memoizer
}
