package dev.kbroom.fluent.bundle.resolver

import dev.kbroom.fluent.bundle.FluentArgs
import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentError
import dev.kbroom.fluent.syntax.CallArguments
import dev.kbroom.fluent.syntax.Expression
import dev.kbroom.fluent.syntax.Identifier
import dev.kbroom.fluent.syntax.InlineExpression
import dev.kbroom.fluent.syntax.Pattern
import dev.kbroom.fluent.syntax.PatternElement
import dev.kbroom.fluent.syntax.Variant
import dev.kbroom.fluent.syntax.VariantKey
import dev.kbroom.fluent.bundle.types.FluentNumber
import dev.kbroom.fluent.bundle.types.FluentValue

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
    val bundle: FluentBundle,
    val args: FluentArgs?,
    val errors: MutableList<FluentError> = mutableListOf(),
    private val placeables: MutableSet<String> = mutableSetOf()
) {
    fun getPlaceables(): Set<String> = placeables.toSet()
    
    fun trackPlaceable(id: String): Boolean {
        if (placeables.contains(id)) {
            errors.add(FluentError.ResolverError(ResolverError.Cyclic))
            return false
        }
        placeables.add(id)
        return true
    }
    
    fun untrackPlaceable(id: String) {
        placeables.remove(id)
    }
}
/**
 * Resolves a Pattern to a formatted string.
 *
 * PatternResolver handles the core logic of converting a parsed Fluent Pattern
 * into a string, including:
 * - Resolving variable references ($var)
 * - Resolving message and term references
 * - Evaluating select expressions
 * - Calling functions
 * - Applying Unicode isolation marks for bidirectional text
 * - Applying transform functions
 *
 * This is the main workhorse of the Fluent runtime.
 *
 * @see Scope for the resolution context
 */
class PatternResolver {
    
    fun resolve(pattern: Pattern, scope: Scope, applyTransform: Boolean = true): String {
        val sb = StringBuilder()
        val useIsolating = scope.bundle.useIsolating
        val len = pattern.elements.size
        val transform = if (applyTransform) scope.bundle.getTransform() else null
        for (element in pattern.elements) {
            when (element) {
                is PatternElement.TextElement -> {
                    val text = element.value
                    sb.append(transform?.invoke(text) ?: text)
                }
                is PatternElement.Placeable -> {
                    val needsIsolation = useIsolating && len > 1 && !isMessageOrTermOrString(element.expression)
                    if (needsIsolation) {
                        sb.append('\u2068')
                    }
                    val value = resolveExpression(element.expression, scope)
                    val resolved = when (value) {
                        is FluentValue.Pattern -> resolve(value.pattern, scope, applyTransform)
                        is FluentValue.None -> {
                            when (val expr = element.expression) {
                                is Expression.Inline -> formatInlineReference(expr.expression)
                                is Expression.Select -> resolveSelect(expr.selector, expr.variants, scope)
                            }
                        }
                        else -> value.asString()
                    }
                    sb.append(resolved)
                    if (needsIsolation) {
                        sb.append('\u2069')
                    }
                }
            }
        }
        return sb.toString()
    }
    
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
    
    fun resolveExpressionAsString(expression: Expression, scope: Scope): String {
        val value = resolveExpression(expression, scope)
        return if (value is FluentValue.None) {
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
    
    private fun resolveMessageReference(id: String, attribute: String?, scope: Scope): FluentValue {
        if (!scope.trackPlaceable(id)) {
            scope.untrackPlaceable(id)
            return FluentValue.Str("{$id}")
        }
        
        val message = scope.bundle.getMessage(id) ?: run {
            val refId = if (attribute != null) "$id.$attribute" else id
            scope.errors.add(FluentError.ResolverError(
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
                scope.errors.add(FluentError.ResolverError(
                    ResolverError.Reference(ReferenceKind.MESSAGE, "$id.$attribute")
                ))
                scope.untrackPlaceable(id)
                return FluentValue.Str("{$id.$attribute}")
            }
        }
        val value = message.value()
        val hasContent = value != null && value.elements.isNotEmpty()
        
        if (hasContent) {
            val result = resolve(value, scope, applyTransform = false)
            scope.untrackPlaceable(id)
            return FluentValue.Str(result)
        }
        
        scope.errors.add(FluentError.ResolverError(
            ResolverError.Reference(ReferenceKind.MESSAGE, id)
        ))
        scope.untrackPlaceable(id)
        return FluentValue.Str("{$id}")
    }
    
    private fun resolveTermReference(
        id: String,
        attribute: String?,
        arguments: CallArguments?,
        scope: Scope
    ): FluentValue {
        val trackId = "-$id"
        if (!scope.trackPlaceable(trackId)) {
            return FluentValue.Str("{-$id}")
        }
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
            if (attribute != null && attrValue != null) {
                val resolveScope = if (arguments != null && (arguments.positional.isNotEmpty() || arguments.named.isNotEmpty())) {
                    val termArgs = FluentArgs()
                    for (named in arguments.named) {
                        val argValue = resolveInlineExpression(named.value, scope)
                        termArgs.set(named.name.name, argValue)
                    }
                    Scope(scope.bundle, termArgs, scope.errors)
                } else {
                    val emptyArgs = FluentArgs()
                    Scope(scope.bundle, emptyArgs, scope.errors)
                }
                scope.untrackPlaceable(trackId)
                val resolvedAttr = resolve(attrValue, resolveScope)
                return FluentValue.Str(resolvedAttr)
            }
            
            val termPattern = term.value()
            val hasPlaceables = termPattern.elements.any { it is PatternElement.Placeable }
            val hasExplicitArgs = arguments != null && 
                (arguments.positional.isNotEmpty() || arguments.named.isNotEmpty())
            
            val resolveScope: Scope = when {
                hasExplicitArgs && arguments != null -> {
                    val termArgs = FluentArgs()
                    for (named in arguments.named) {
                        val argValue = resolveInlineExpression(named.value, scope)
                        termArgs.set(named.name.name, argValue)
                    }
                    Scope(scope.bundle, termArgs, scope.errors)
                }
                scope.args != null -> {
                    val emptyArgs = FluentArgs()
                    Scope(scope.bundle, emptyArgs, scope.errors)
                }
                else -> scope
            }
            
            scope.untrackPlaceable(trackId)
            return FluentValue.Str(resolve(term.value(), resolveScope))
        }
        
        val result = resolveMessageReference(id, attribute, scope)
        
        scope.untrackPlaceable(trackId)
        return when (result) {
            is FluentValue.None -> {
                scope.errors.add(FluentError.ResolverError(
                    ResolverError.Reference(ReferenceKind.TERM, id)
                ))
                FluentValue.Str("{-$id}")
            }
            is FluentValue.Str -> {
                if (result.value.startsWith("{") && result.value.endsWith("}")) {
                    FluentValue.Str("{-$id}")
                } else {
                    result
                }
            }
            else -> result
        }
    }
    
    private fun resolveVariable(id: String, scope: Scope): FluentValue {
        val value = scope.args?.get(id)
        if (value != null) return value
        
        scope.errors.add(FluentError.ResolverError(
            ResolverError.Reference(ReferenceKind.VARIABLE, id)
        ))
        return FluentValue.Str("{$$id}")
    }
    
    private fun resolveFunction(id: String, arguments: CallArguments, scope: Scope): FluentValue {
        val fn = scope.bundle.getFunction(id)
        if (fn == null) {
            scope.errors.add(FluentError.ResolverError(
                ResolverError.Reference(ReferenceKind.FUNCTION, id)
            ))
            return FluentValue.Str("{$id(...)}")
        }
        
        val positionalArgs = arguments.positional.map { resolveInlineExpression(it, scope) }
        val namedArgs = arguments.named.associate { it.name.name to resolveInlineExpression(it.value, scope) }
        
        val argsObj = object {
            operator fun get(name: String): FluentValue? = namedArgs[name]
        }
        
        return try {
            val fluentArgs = FluentArgs()
            namedArgs.forEach { (name, value) -> fluentArgs.set(name, value) }
            positionalArgs.forEach { fluentArgs.add(it) }
            fn(positionalArgs, fluentArgs)
        } catch (e: Exception) {
            FluentValue.Error("Function error: ${e.message}")
        }
    }
    
    private fun resolveSelect(
        selector: InlineExpression,
        variants: List<Variant>,
        scope: Scope
    ): FluentValue {
        val selectorValue = resolveInlineExpression(selector, scope)
        
        // Find matching variant
        val matchingVariant = variants.find { variant ->
            when (val key = variant.key) {
                is VariantKey.Identifier -> {
                    val selectorStr = selectorValue.asString()
                    key.name == selectorStr || 
                    (selectorValue is FluentValue.Number && key.name == selectorValue.value.value.toInt().toString())
                }
                is VariantKey.NumberLiteral -> {
                    selectorValue is FluentValue.Number && 
                    key.value == selectorValue.value.value.toInt().toString()
                }
                else -> false
            }
        }
        
        val selectedVariant = matchingVariant ?: variants.find { it.default }
        
        if (selectedVariant == null) {
            scope.errors.add(FluentError.ResolverError(ResolverError.MissingDefault))
            return FluentValue.None
        }
        
        return when (val variantValue = selectedVariant.value) {
            is Pattern -> {
                val resolved = resolve(variantValue, scope)
                FluentValue.Str(resolved)
            }
            else -> FluentValue.None
        }
    }
}
