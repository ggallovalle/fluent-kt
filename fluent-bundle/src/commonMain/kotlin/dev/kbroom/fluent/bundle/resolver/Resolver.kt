package dev.kbroom.fluent.bundle.resolver

import dev.kbroom.fluent.bundle.FluentArgs
import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentError
import dev.kbroom.fluent.bundle.FluentMessage
import dev.kbroom.fluent.bundle.FluentTerm
import dev.kbroom.fluent.bundle.IntlHelpers
import dev.kbroom.fluent.bundle.types.FluentNumber
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.syntax.CallArguments
import dev.kbroom.fluent.syntax.Expression
import dev.kbroom.fluent.syntax.Identifier
import dev.kbroom.fluent.syntax.InlineExpression
import dev.kbroom.fluent.syntax.Pattern
import dev.kbroom.fluent.syntax.PatternElement
import dev.kbroom.fluent.syntax.Variant
import dev.kbroom.fluent.syntax.VariantKey

@Suppress("AbstractClassCanBeInterface")
sealed class ResolverError {
    data class Reference(val kind: ReferenceKind, val id: String) : ResolverError()
    data object NoValue : ResolverError()
    data object MissingDefault : ResolverError()
    data object Cyclic : ResolverError()
    data object TooManyPlaceables : ResolverError()
}

enum class ReferenceKind {
    MESSAGE,
    TERM,
    VARIABLE,
    FUNCTION,
}

class Scope(
    val bundle: FluentBundle,
    val args: FluentArgs?,
    val errors: MutableList<FluentError> = mutableListOf(),
    private val placeables: MutableSet<String> = mutableSetOf(),
    val rootMessageId: String? = null,
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

@Suppress("TooManyFunctions", "CyclomaticComplexMethod")
class PatternResolver {
    fun resolve(pattern: Pattern, scope: Scope, applyTransform: Boolean = true): String {
        val sb = StringBuilder()
        val useIsolating = scope.bundle.useIsolating
        val transform = if (applyTransform) scope.bundle.getTransform() else null
        for (element in pattern.elements) {
            when (element) {
                is PatternElement.TextElement -> sb.append(transform?.invoke(element.value) ?: element.value)
                is PatternElement.Placeable -> appendPlaceable(sb, element, useIsolating, pattern.elements.size, scope)
            }
        }
        return sb.toString()
    }

    private fun appendPlaceable(
        sb: StringBuilder,
        element: PatternElement.Placeable,
        useIsolating: Boolean,
        totalElements: Int,
        scope: Scope,
    ) {
        val needsIsolation = useIsolating && totalElements > 1 && !isMessageOrTermOrString(element.expression)
        if (needsIsolation) sb.append('\u2068')
        val value = resolveExpression(element.expression, scope)
        sb.append(renderPlaceableValue(value, element.expression, scope))
        if (needsIsolation) sb.append('\u2069')
    }

    private fun renderPlaceableValue(value: FluentValue, expression: Expression, scope: Scope): String {
        val transform = scope.bundle.getTransform()
        return when (value) {
            is FluentValue.Pattern -> resolve(value.pattern, scope, applyTransform = true)

            is FluentValue.None -> when (expression) {
                is Expression.Inline -> formatInlineReference(expression.expression)
                is Expression.Select -> resolveSelect(expression.selector, expression.variants, scope).asString()
            }

            else -> {
                val str = value.asString()
                // Apply transform to resolved message/term values but not to
                // string literals, variables, or function results
                val shouldTransform = transform != null &&
                    expression is Expression.Inline &&
                    (
                        expression.expression is InlineExpression.MessageReference ||
                            expression.expression is InlineExpression.TermReference
                        )
                val t = transform
                if (shouldTransform && t != null) t(str) else str
            }
        }
    }

    private fun isMessageOrTermOrString(expression: Expression): Boolean = when (expression) {
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

    fun resolveExpression(expression: Expression, scope: Scope): FluentValue = when (expression) {
        is Expression.Select -> {
            resolveSelect(expression.selector, expression.variants, scope)
        }

        is Expression.Inline -> {
            resolveInlineExpression(expression.expression, scope)
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

    private fun formatInlineReference(expr: InlineExpression): String = when (expr) {
        is InlineExpression.MessageReference -> "{${expr.id.name}}"
        is InlineExpression.TermReference -> "{-${expr.id.name}}"
        is InlineExpression.VariableReference -> "{\$${expr.id.name}}"
        is InlineExpression.FunctionReference -> "${expr.id.name}()"
        is InlineExpression.StringLiteral -> expr.value
        is InlineExpression.NumberLiteral -> expr.value
        is InlineExpression.Placeable -> "{...}"
    }

    fun resolveInlineExpression(expression: InlineExpression, scope: Scope): FluentValue = when (expression) {
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

    private fun resolveMessageReference(id: String, attribute: String?, scope: Scope): FluentValue {
        if (!scope.trackPlaceable(id)) {
            val fallbackId = scope.rootMessageId ?: id
            return FluentValue.Str("{$fallbackId}")
        }
        val message = scope.bundle.getMessage(id)
        val result: FluentValue = when {
            message == null -> reportMissingMessage(id, attribute, scope)
            attribute != null -> resolveMessageAttribute(id, message, attribute, scope)
            else -> resolveMessageBody(id, message, scope)
        }
        scope.untrackPlaceable(id)
        return result
    }

    private fun reportMissingMessage(id: String, attribute: String?, scope: Scope): FluentValue {
        val refId = if (attribute != null) "$id.$attribute" else id
        scope.errors.add(
            FluentError.ResolverError(
                ResolverError.Reference(ReferenceKind.MESSAGE, refId),
            ),
        )
        return FluentValue.Str("{$refId}")
    }

    private fun resolveMessageAttribute(
        id: String,
        message: FluentMessage,
        attribute: String,
        scope: Scope,
    ): FluentValue {
        val attrValue = message.getAttributeValue(attribute)
        if (attrValue != null) return FluentValue.Pattern(attrValue)
        scope.errors.add(
            FluentError.ResolverError(
                ResolverError.Reference(ReferenceKind.MESSAGE, "$id.$attribute"),
            ),
        )
        return FluentValue.Str("{$id.$attribute}")
    }

    private fun resolveMessageBody(id: String, message: FluentMessage, scope: Scope): FluentValue {
        val value = message.value()
        val hasContent = value != null && value.elements.isNotEmpty()
        if (hasContent) return FluentValue.Str(resolve(value, scope, applyTransform = false))
        scope.errors.add(
            FluentError.ResolverError(
                ResolverError.NoValue,
            ),
        )
        return FluentValue.Str("{$id}")
    }

    private fun resolveTermReference(
        id: String,
        attribute: String?,
        arguments: CallArguments?,
        scope: Scope,
    ): FluentValue {
        val trackId = "-$id"
        if (!scope.trackPlaceable(trackId)) {
            return FluentValue.Str("{-$id}")
        }
        val term = scope.bundle.getTerm(id)
        val result: FluentValue = if (term != null) {
            resolveTermHit(id, attribute, arguments, term, scope)
        } else {
            resolveTermAsMessage(id, attribute, scope)
        }
        scope.untrackPlaceable(trackId)
        return result
    }

    private fun resolveTermHit(
        id: String,
        attribute: String?,
        arguments: CallArguments?,
        term: FluentTerm,
        scope: Scope,
    ): FluentValue {
        if (attribute == null) return resolveTermBody(term, arguments, scope)
        val attrValue = term.getAttributeValue(attribute) ?: return FluentValue.Str("{-$id.$attribute}")
        val termErrors = mutableListOf<FluentError>()
        val termScope = scopeForTerm(arguments, scope).let { s ->
            Scope(s.bundle, s.args, termErrors)
        }
        return FluentValue.Str(resolve(attrValue, termScope))
    }

    private fun resolveTermBody(term: FluentTerm, arguments: CallArguments?, scope: Scope): FluentValue {
        val hasExplicitArgs = arguments != null &&
            (arguments.positional.isNotEmpty() || arguments.named.isNotEmpty())
        val termErrors = mutableListOf<FluentError>()
        val resolveScope: Scope = when {
            hasExplicitArgs -> {
                val s = scopeForTerm(arguments, scope)
                Scope(s.bundle, s.args, termErrors)
            }

            scope.args != null -> Scope(scope.bundle, FluentArgs(), termErrors)

            else -> Scope(scope.bundle, scope.args, termErrors)
        }
        return FluentValue.Str(resolve(term.value(), resolveScope))
    }

    private fun scopeForTerm(arguments: CallArguments?, scope: Scope): Scope {
        if (arguments == null || (arguments.positional.isEmpty() && arguments.named.isEmpty())) {
            return Scope(scope.bundle, FluentArgs(), scope.errors)
        }
        val termArgs = FluentArgs()
        for (named in arguments.named) {
            termArgs.set(named.name.name, resolveInlineExpression(named.value, scope))
        }
        return Scope(scope.bundle, termArgs, scope.errors)
    }

    private fun resolveTermAsMessage(id: String, attribute: String?, scope: Scope): FluentValue {
        val result = resolveMessageReference(id, attribute, scope)
        return when (result) {
            is FluentValue.None -> reportTermFallback(id, scope)
            is FluentValue.Str -> preserveOrSubstitute(result, id)
            else -> result
        }
    }

    private fun reportTermFallback(id: String, scope: Scope): FluentValue {
        scope.errors.add(
            FluentError.ResolverError(ResolverError.Reference(ReferenceKind.TERM, id)),
        )
        return FluentValue.Str("{-$id}")
    }

    private fun preserveOrSubstitute(result: FluentValue.Str, id: String): FluentValue =
        if (result.value.startsWith("{") && result.value.endsWith("}")) {
            FluentValue.Str("{-$id}")
        } else {
            result
        }

    private fun resolveVariable(id: String, scope: Scope): FluentValue {
        val value = scope.args?.get(id)
        if (value != null) return value

        scope.errors.add(
            FluentError.ResolverError(
                ResolverError.Reference(ReferenceKind.VARIABLE, id),
            ),
        )
        return FluentValue.Str("{\$$id}")
    }

    private fun resolveFunction(id: String, arguments: CallArguments, scope: Scope): FluentValue {
        val fn = scope.bundle.getFunction(id)
        if (fn == null) {
            scope.errors.add(
                FluentError.ResolverError(
                    ResolverError.Reference(ReferenceKind.FUNCTION, id),
                ),
            )
            return FluentValue.Str("{$id()}")
        }

        val positionalArgs = arguments.positional.map { resolveInlineExpression(it, scope) }
        val namedArgs = arguments.named.associate { it.name.name to resolveInlineExpression(it.value, scope) }

        return try {
            val fluentArgs = FluentArgs()
            namedArgs.forEach { (name, value) -> fluentArgs.set(name, value) }
            positionalArgs.forEach { fluentArgs.add(it) }
            fn(positionalArgs, fluentArgs)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            FluentValue.Error("Function error: ${e.message}")
        }
    }

    private fun resolveSelect(selector: InlineExpression, variants: List<Variant>, scope: Scope): FluentValue {
        val selectorValue = resolveInlineExpression(selector, scope)

        val matchingVariant = variants.find { variant ->
            when (val key = variant.key) {
                is VariantKey.Identifier -> matchesIdentifier(key, selectorValue, scope)
                is VariantKey.NumberLiteral -> matchesNumberLiteral(key, selectorValue)
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

    private fun matchesIdentifier(key: VariantKey.Identifier, selectorValue: FluentValue, scope: Scope): Boolean {
        val selectorStr = selectorValue.asString()
        val numberKey = (selectorValue as? FluentValue.Number)
            ?.value?.value?.toInt()?.toString()
        val numericValue = when (selectorValue) {
            is FluentValue.Number -> selectorValue.value.value
            is FluentValue.Str -> selectorValue.value.toDoubleOrNull()
            else -> null
        }
        val pluralCategory = if (numericValue != null) {
            IntlHelpers.getPluralCategory(
                numericValue,
                scope.bundle.locales.first(),
                scope.bundle.memoizer(),
            )
        } else {
            null
        }
        return key.name == selectorStr ||
            (numberKey != null && key.name == numberKey) ||
            (pluralCategory != null && key.name == pluralCategory)
    }

    private fun matchesNumberLiteral(key: VariantKey.NumberLiteral, selectorValue: FluentValue): Boolean {
        if (selectorValue is FluentValue.Number && key.value == selectorValue.value.value.toInt().toString()) {
            return true
        }
        return selectorValue is FluentValue.Str && key.value == selectorValue.value
    }
}
