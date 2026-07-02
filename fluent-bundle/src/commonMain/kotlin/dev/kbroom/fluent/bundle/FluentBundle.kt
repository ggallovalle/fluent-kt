package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.syntax.*
import dev.kbroom.fluent.bundle.resolver.PatternResolver
import dev.kbroom.fluent.bundle.resolver.Scope
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.intl.IntlLangMemoizer
import dev.kbroom.fluent.bundle.types.getPluralCategory

/**
 * FluentBundle is the main runtime for localization.
 */
class FluentBundle(
    val locales: List<LanguageIdentifier>,
    private val useIsolating: Boolean = true,
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
        return entries[id] is Entry.Message
    }
    
    fun getTerm(id: String): FluentTerm? {
        val entry = entries[id] ?: return null
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
                    is FluentValue.Number -> FluentValue.Str(num.value.value.toString())
                    is FluentValue.Str -> {
                        val d = num.value.toDoubleOrNull() ?: 0.0
                        FluentValue.Str(d.toString())
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
    }
    
    fun memoizer(): IntlLangMemoizer = memoizer
}
