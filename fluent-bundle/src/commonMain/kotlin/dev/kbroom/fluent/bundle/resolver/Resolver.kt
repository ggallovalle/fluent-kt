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
    // Public getter for debugging
    fun getPlaceables(): Set<String> = placeables.toSet()
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

class PatternResolver {
    
    /**
     * Resolve a pattern to a string.
     * @param pattern The pattern to resolve
     * @param scope The resolution scope
     * @param applyTransform Whether to apply transform to text elements. Should be false when resolving referenced patterns.
     */
    fun resolve(pattern: Pattern, scope: Scope, applyTransform: Boolean = true): String {
        val sb = StringBuilder()
        val useIsolating = scope.bundle.useIsolating
        val len = pattern.elements.size
        val transform = if (applyTransform) scope.bundle.getTransform() else null
        for (element in pattern.elements) {
            when (element) {
                is PatternElement.TextElement -> {
                    // Transform text elements only if applyTransform is true
                    val text = element.value
                    sb.append(transform?.invoke(text) ?: text)
                }
                is PatternElement.Placeable -> {
                    // Check if we need isolation marks
                    val needsIsolation = useIsolating && len > 1 && !isMessageOrTermOrString(element.expression)
                    if (needsIsolation) {
                        sb.append('\u2068') // FSI
                    }
                    val value = resolveExpression(element.expression, scope)
                    // Handle Pattern values - resolve them recursively
                    val resolved = when (value) {
                        is FluentValue.Pattern -> resolve(value.pattern, scope, applyTransform)
                        is FluentValue.None -> {
                            when (val expr = element.expression) {
                                is Expression.Inline -> formatInlineReference(expr.expression)
                                is Expression.Select -> "{...}"
                            }
                        }
                        is FluentValue.Error -> {
                            val errMsg = value.message
                            "{$errMsg}"
                        }
                    else -> value.asString()
                    }
                    // Don't transform placeable output - only TextElements get transformed
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
            // Cycle detected - already tracking this id, return raw reference
            // Untrack first since we're returning early
            scope.untrackPlaceable(id)
            return FluentValue.Str("{$id}")
        }
        
        val message = scope.bundle.getMessage(id) ?: run {
            // Message not found - return reference format
            // If attribute was specified, include it in the reference
            val refId = if (attribute != null) "$id.$attribute" else id
            scope.errors.add(dev.kbroom.fluent.bundle.FluentError.ResolverError(
                ResolverError.Reference(ReferenceKind.MESSAGE, refId)
            ))
            scope.untrackPlaceable(id)
            return FluentValue.Str("{$refId}")
        }
        
        if (attribute != null) {
            val attrValue = message.getAttributeValue(attribute)
            if (attrValue != null) {
                scope.untrackPlaceable(id)
                return FluentValue.Pattern(attrValue)
            } else {
                scope.errors.add(dev.kbroom.fluent.bundle.FluentError.ResolverError(
                    ResolverError.Reference(ReferenceKind.MESSAGE, "$id.$attribute")
                ))
                scope.untrackPlaceable(id)
                return FluentValue.Str("{$id.$attribute}")
            }
        }
        val value = message.value()
        // Check if value exists and has content (empty pattern should be treated as no value)
        val hasContent = value != null && value.elements.isNotEmpty()
        
        if (hasContent) {
            // Don't apply transform when resolving referenced patterns
            // This ensures {foo} in pattern A doesn't get transformed when foo is resolved
            val result = resolve(value, scope, applyTransform = false)
            scope.untrackPlaceable(id)
            return FluentValue.Str(result)
        }
        
        // Message has no value - return reference format
        scope.errors.add(dev.kbroom.fluent.bundle.FluentError.ResolverError(
            ResolverError.Reference(ReferenceKind.MESSAGE, id)
        ))
        scope.untrackPlaceable(id)
        return FluentValue.Str("{$id}")
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
            // Return resolved value for attribute to allow proper select handling
            if (attribute != null) {
                // Build scope with provided arguments if any
                val resolveScope = if (arguments != null && (arguments.positional.isNotEmpty() || arguments.named.isNotEmpty())) {
                    val termArgs = dev.kbroom.fluent.bundle.FluentArgs()
                    for (named in arguments.named) {
                        val argValue = resolveInlineExpression(named.value, scope)
                        termArgs.set(named.name.name, argValue)
                    }
                    Scope(scope.bundle, termArgs, scope.errors)
                } else {
                    // No arguments - use empty scope to block external args
                    val emptyArgs = dev.kbroom.fluent.bundle.FluentArgs()
                    Scope(scope.bundle, emptyArgs, scope.errors)
                }
                scope.untrackPlaceable(trackId)
                val resolvedAttr = resolve(attrValue!!, resolveScope)
                return FluentValue.Str(resolvedAttr)
            }
            
            // Check if term's pattern has any placeables
            // If so, use empty args to prevent external args from leaking in
            val termPattern = term.value()
            val hasPlaceables = termPattern.elements.any { it is PatternElement.Placeable }
            
            // Check if explicit arguments were provided to this term reference
            val hasExplicitArgs = arguments != null && 
                (arguments.positional.isNotEmpty() || arguments.named.isNotEmpty())
            
            // Determine which scope to use
            val resolveScope: Scope = when {
                // If explicit args provided, use them
                hasExplicitArgs -> {
                    val termArgs = dev.kbroom.fluent.bundle.FluentArgs()
                    for (named in arguments!!.named) {
                        val argValue = resolveInlineExpression(named.value, scope)
                        termArgs.set(named.name.name, argValue)
                    }
                    Scope(scope.bundle, termArgs, scope.errors)
                }
                // If no explicit args but external args exist, use empty to block
                scope.args != null -> {
                    val emptyArgs = dev.kbroom.fluent.bundle.FluentArgs()
                    Scope(scope.bundle, emptyArgs, scope.errors)
                }
                else -> scope
            }
            
            // Untrack before resolving to allow self-references
            scope.untrackPlaceable(trackId)
            return FluentValue.Str(resolve(term.value(), resolveScope))
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
        val key: String? = when (selectorValue) {
            is FluentValue.Str -> selectorValue.value
            is FluentValue.Number -> selectorValue.value.value.toInt().toString()
            is FluentValue.None -> null  // Use default variant
            is FluentValue.Pattern -> {
                // Resolve the pattern to a string for comparison
                val resolved = resolve(selectorValue.pattern, scope)
                resolved.ifEmpty { null }
            }
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
