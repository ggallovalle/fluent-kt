package dev.kbroom.fluent.bundle.resolver

import dev.kbroom.fluent.syntax.*
import dev.kbroom.fluent.bundle.types.*

/**
 * Errors that can occur during resolution.
 */
sealed class ResolverError {
    /**
     * Reference to an unknown message or term.
     */
    data class Reference(val kind: ReferenceKind, val id: String) : ResolverError()
    
    /**
     * No value was found.
     */
    data object NoValue : ResolverError()
    
    /**
     * Missing default variant in select.
     */
    data object MissingDefault : ResolverError()
    
    /**
     * Cyclic reference detected.
     */
    data object Cyclic : ResolverError()
    
    /**
     * Too many placeables (limit exceeded).
     */
    data object TooManyPlaceables : ResolverError()
}

/**
 * Kind of reference error.
 */
enum class ReferenceKind {
    MESSAGE,
    TERM,
    VARIABLE,
    FUNCTION
}

/**
 * Scope holds the current resolution context.
 */
class Scope(
    val bundle: dev.kbroom.fluent.bundle.FluentBundle,
    val args: dev.kbroom.fluent.bundle.FluentArgs?,
    val errors: MutableList<dev.kbroom.fluent.bundle.FluentError> = mutableListOf(),
    private val placeables: MutableSet<String> = mutableSetOf()
) {
    private var dirty = false
    
    /**
     * Track a placeable for cycle detection.
     */
    fun trackPlaceable(id: String): Boolean {
        if (placeables.contains(id)) {
            errors.add(dev.kbroom.fluent.bundle.FluentError.ResolverError(ResolverError.Cyclic))
            return false
        }
        placeables.add(id)
        return true
    }
    
    /**
     * Untrack a placeable after resolution.
     */
    fun untrackPlaceable(id: String) {
        placeables.remove(id)
    }
    
    /**
     * Mark scope as dirty (message was overridden).
     */
    fun setDirty() { dirty = true }
    
    /**
     * Check if scope is dirty.
     */
    fun isDirty(): Boolean = dirty
}

/**
 * Pattern resolver - converts AST Pattern to formatted string.
 */
class PatternResolver {
    
    /**
     * Resolve a pattern to a string.
     */
    fun resolve(pattern: Pattern, scope: Scope): String {
        val sb = StringBuilder()
        val useIsolating = scope.bundle.useIsolating
        val len = pattern.elements.size
        
        for (element in pattern.elements) {
            when (element) {
                is PatternElement.TextElement -> {
                    sb.append(element.value)
                }
                is PatternElement.Placeable -> {
                    // Check if we need isolation marks
                    val needsIsolation = useIsolating && len > 1 && !isMessageOrTermOrString(element.expression)
                    if (needsIsolation) {
                        sb.append('\u2068') // FSI
                    }
                    val value = resolveExpression(element.expression, scope)
                    if (element.expression.toString().contains("foo") || element.expression.toString().contains("attr")) {
                        println("DEBUG: expr=${element.expression}, value=$value")
                    }
                    // Handle Pattern values - resolve them recursively
                    val resolved = when (value) {
                        is FluentValue.Pattern -> resolve(value.pattern, scope)
                        is FluentValue.None -> {
                            when (val expr = element.expression) {
                                is Expression.Inline -> formatInlineReference(expr.expression)
                                is Expression.Select -> "{...}"
                            }
                        }
                        else -> value.asString()
                    }
                    sb.append(resolved)
                    if (needsIsolation) {
                        sb.append('\u2069') // PDI
                    }
                }
            }
        }
        return sb.toString()
    }
    
    /**
     * Check if an expression doesn't need isolation marks.
     */
    private fun isMessageOrTermOrString(expression: Expression): Boolean {
        return when (expression) {
            is Expression.Inline -> {
                when (expression.expression) {
                    is InlineExpression.MessageReference -> true
                    is InlineExpression.TermReference -> true
                    is InlineExpression.StringLiteral -> true
                    else -> false
                }
            }
            else -> false
        }
    }
    
    /**
     * Resolve an expression to a FluentValue.
     */
    fun resolveExpression(expression: Expression, scope: Scope): FluentValue {
        return when (expression) {
            is Expression.Select -> {
                resolveSelect(expression.selector, expression.variants, scope)
            }
            is Expression.Inline -> {
                resolveInlineExpression(expression.expression, scope)
            }
        }
    }
    
    /**
     * Resolve an expression and return as formatted string or original reference.
     */
    fun resolveExpressionAsString(expression: Expression, scope: Scope): String {
        val value = resolveExpression(expression, scope)
        return if (value is FluentValue.None) {
            // Return the original reference for missing values
            when (expression) {
                is Expression.Inline -> formatInlineReference(expression.expression)
                is Expression.Select -> "{...}"
            }
        } else {
            value.asString()
        }
    }
    
    private fun formatInlineReference(expr: InlineExpression): String {
        return when (expr) {
            is InlineExpression.MessageReference -> "{${expr.id.name}}"
            is InlineExpression.TermReference -> "{-${expr.id.name}}"
            is InlineExpression.VariableReference -> "{$${expr.id.name}}"
            is InlineExpression.FunctionReference -> "${expr.id.name}(...)"
            is InlineExpression.StringLiteral -> expr.value
            is InlineExpression.NumberLiteral -> expr.value
            is InlineExpression.Placeable -> "{...}"
        }
    }
    
    /**
     * Resolve an inline expression.
     */
    fun resolveInlineExpression(
        expression: InlineExpression,
        scope: Scope
    ): FluentValue {
        return when (expression) {
            is InlineExpression.StringLiteral -> {
                FluentValue.Str(expression.value)
            }
            is InlineExpression.NumberLiteral -> {
                FluentValue.Number(FluentNumber(expression.value.toDoubleOrNull() ?: 0.0))
            }
            is InlineExpression.MessageReference -> {
                resolveMessageReference(expression.id.name, expression.attribute?.name, scope)
            }
            is InlineExpression.TermReference -> {
                resolveTermReference(expression.id.name, expression.attribute?.name, expression.arguments, scope)
            }
            is InlineExpression.VariableReference -> {
                resolveVariable(expression.id.name, scope)
            }
            is InlineExpression.FunctionReference -> {
                resolveFunction(expression.id.name, expression.arguments, scope)
            }
            is InlineExpression.Placeable -> {
                resolveExpression(expression.expression, scope)
            }
        }
    }
    /**
     * Resolve a message reference.
     */
    private fun resolveMessageReference(id: String, attribute: String?, scope: Scope): FluentValue {
        // Track for cycle detection
        if (!scope.trackPlaceable(id)) {
            return FluentValue.Str("{$id}")
        }
        
        val message = scope.bundle.getMessage(id) ?: run {
            scope.errors.add(dev.kbroom.fluent.bundle.FluentError.ResolverError(
                ResolverError.Reference(ReferenceKind.MESSAGE, id)
            ))
            scope.untrackPlaceable(id)
            return FluentValue.Str("{$id}")
        }
        
        val result = if (attribute != null) {
            val attrValue = message.getAttributeValue(attribute)
            if (attrValue != null) {
                // Untrack before resolving to allow self-references
                scope.untrackPlaceable(id)
                // Return Pattern to allow proper select expression handling
                FluentValue.Pattern(attrValue)
            } else {
                scope.errors.add(dev.kbroom.fluent.bundle.FluentError.ResolverError(
                    ResolverError.Reference(ReferenceKind.MESSAGE, "$id.$attribute")
                ))
                FluentValue.Str("{$id.$attribute}")
            }
        } else {
            val value = message.value()
            if (value != null) {
                FluentValue.Str(resolve(value, scope))
            } else {
                // Message has no value - this is a NoValue case
                scope.errors.add(dev.kbroom.fluent.bundle.FluentError.ResolverError(
                    ResolverError.Reference(ReferenceKind.MESSAGE, id)
                ))
                FluentValue.None
            }
        }
        
        scope.untrackPlaceable(id)
        return result
    }
    
    /**
     * Resolve a term reference.
     */
    private fun resolveTermReference(
        id: String,
        attribute: String?,
        arguments: CallArguments?,
        scope: Scope
    ): FluentValue {
        // Track for cycle detection
        val trackId = "-$id"
        if (!scope.trackPlaceable(trackId)) {
            return FluentValue.Str("{-$id}")
        }
        // Look up term in bundle - if found, resolve it
        val term = scope.bundle.getTerm(id)
        if (term != null) {
            val attrValue = if (attribute != null) {
                term.getAttributeValue(attribute)
            } else {
                null
            }
            if (attribute != null && attrValue == null) {
                return FluentValue.Str("{-$id.$attribute}")
            }
            // Return pattern for attribute values to allow proper select handling
            if (attribute != null) {
                scope.untrackPlaceable(trackId)
                return FluentValue.Pattern(attrValue!!)
            }
            // Untrack before resolving to allow self-references
            scope.untrackPlaceable(trackId)
            return FluentValue.Str(resolve(term.value(), scope))
        }
        
        // Term not found - try as message reference
        val result = resolveMessageReference(id, attribute, scope)
        
        // Transform to term reference format
        scope.untrackPlaceable(trackId)
        return when (result) {
            is FluentValue.None -> {
                scope.errors.add(dev.kbroom.fluent.bundle.FluentError.ResolverError(
                    ResolverError.Reference(ReferenceKind.TERM, id)
                ))
                FluentValue.Str("{-$id}")
            }
            is FluentValue.Str -> {
                // If result is a fallback reference like {missing}, convert to term format {-missing}
                if (result.value.startsWith("{") && result.value.endsWith("}")) {
                    FluentValue.Str("{-$id}")
                } else {
                    result
                }
            }
            else -> result
        }
    }
    
    /**
     * Resolve a variable reference.
     */
    private fun resolveVariable(id: String, scope: Scope): FluentValue {
        val value = scope.args?.get(id)
        if (value != null) return value
        
        scope.errors.add(dev.kbroom.fluent.bundle.FluentError.ResolverError(
            ResolverError.Reference(ReferenceKind.VARIABLE, id)
        ))
        // Return the variable reference as string
        return FluentValue.Str("{$$id}")
    }
    
    /**
     * Resolve a function call.
     */
    private fun resolveFunction(
        name: String,
        arguments: CallArguments,
        scope: Scope
    ): FluentValue {
        val fn = scope.bundle.getFunction(name)
        if (fn != null) {
            val positional = arguments.positional.map { resolveInlineExpression(it, scope) }
            val args = dev.kbroom.fluent.bundle.FluentArgs()
            for (named in arguments.named) {
                val value = resolveInlineExpression(named.value, scope)
                args.set(named.name.name, value.asAny())
            }
            return fn(positional, args)
        }
        
        scope.errors.add(dev.kbroom.fluent.bundle.FluentError.ResolverError(
            ResolverError.Reference(ReferenceKind.FUNCTION, name)
        ))
        return FluentValue.Error("${name}()")
    }
    
    /**
     * Resolve a select expression.
     */
    private fun resolveSelect(
        selector: InlineExpression,
        variants: List<Variant>,
        scope: Scope
    ): FluentValue {
        val selectorValue = resolveInlineExpression(selector, scope)
        
        // Get the key for matching - handle None gracefully
        val key = when (selectorValue) {
            is FluentValue.Str -> selectorValue.value
            is FluentValue.Number -> selectorValue.value.value.toInt().toString()
            is FluentValue.None -> null  // Use default variant
            is FluentValue.Pattern -> selectorValue.pattern.elements.firstOrNull()?.let {
                when (it) {
                    is PatternElement.TextElement -> it.value
                    else -> selectorValue.asString()
                }
            } ?: selectorValue.asString()
            else -> selectorValue.asString()
        }
        
        // Find matching variant - first try exact match, then default
        var defaultVariant: Variant? = null
        
        for (variant in variants) {
            val variantKey = when (val vk = variant.key) {
                is VariantKey.Identifier -> vk.name
                is VariantKey.NumberLiteral -> vk.value
            }
            
            if (variant.default) {
                defaultVariant = variant
            }
            // Check for exact match
            if (key != null && variantKey == key) {
                return FluentValue.Str(resolve(variant.value, scope))
            }
            
            // Check for plural category match (number selector matching identifier like "one", "few", etc.)
            if (selectorValue is FluentValue.Number && variant.key is VariantKey.Identifier) {
                val pluralCategory = getPluralCategory(selectorValue.value.value)
                if (pluralCategory == variantKey) {
                    return FluentValue.Str(resolve(variant.value, scope))
                }
            }
        }
        
        // Return default variant if no match
        return if (defaultVariant != null) {
            FluentValue.Str(resolve(defaultVariant.value, scope))
        } else {
            // No matching variant and no default
            scope.errors.add(dev.kbroom.fluent.bundle.FluentError.ResolverError(ResolverError.MissingDefault))
            FluentValue.None
        }
    }
    
    /**
     * Get the CLDR plural category for a number.
     * Simplified implementation for English locale.
     */
    private fun getPluralCategory(n: Double): String {
        val i = n.toInt()
        return when {
            i == 1 -> "one"
            i == 0 || i > 1 -> "other"
            else -> "other"
        }
    }
}
