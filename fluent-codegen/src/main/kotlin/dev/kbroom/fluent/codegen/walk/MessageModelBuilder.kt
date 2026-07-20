package dev.kbroom.fluent.codegen.walk

import dev.kbroom.fluent.codegen.model.ArgModel
import dev.kbroom.fluent.codegen.model.AttributeModel
import dev.kbroom.fluent.codegen.model.BundleModel
import dev.kbroom.fluent.codegen.model.MessageModel
import dev.kbroom.fluent.syntax.Attribute
import dev.kbroom.fluent.syntax.CallArguments
import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.Expression
import dev.kbroom.fluent.syntax.InlineExpression
import dev.kbroom.fluent.syntax.Pattern
import dev.kbroom.fluent.syntax.PatternElement
import dev.kbroom.fluent.syntax.Resource
import dev.kbroom.fluent.syntax.VariableDoc

/**
 * Walks Fluent ASTs to collect message models (IDs, args, attributes, docs).
 */
object MessageModelBuilder {
    fun fromResources(
        bundleName: String,
        resources: List<Pair<String, Resource>>,
    ): BundleModel {
        val messages = linkedMapOf<String, MessageModel>()
        val resourcePaths = mutableListOf<String>()
        for ((relativeFtl, resource) in resources) {
            val pathWithoutExt = relativeFtl.removeSuffix(".ftl")
            resourcePaths.add(pathWithoutExt)
            for (entry in resource.body) {
                when (entry) {
                    is Entry.Message -> {
                        val model = fromMessage(entry)
                        check(!messages.containsKey(model.id)) {
                            "Duplicate message id '${model.id}' in bundle '$bundleName' " +
                                "(seen in $relativeFtl)"
                        }
                        messages[model.id] = model
                    }
                    is Entry.Term, is Entry.Comment, is Entry.GroupComment,
                    is Entry.ResourceComment, is Entry.Junk,
                    -> Unit
                }
            }
        }
        return BundleModel(
            name = bundleName,
            messages = messages.values.toList(),
            resourcePaths = resourcePaths.distinct(),
        )
    }

    fun fromMessage(message: Entry.Message): MessageModel {
        val docsByName = message.docComment?.variables
            ?.associateBy { it.name }
            .orEmpty()
        val valueArgs = message.value?.let { collectArgs(it) }.orEmpty()
        val args = mergeArgs(valueArgs, docsByName)
        val attributes = message.attributes.map { fromAttribute(it, docsByName) }
        return MessageModel(
            id = message.id.name,
            description = message.docComment?.description.orEmpty().trim(),
            args = args,
            attributes = attributes,
            hasValue = message.value != null,
        )
    }

    private fun fromAttribute(
        attribute: Attribute,
        parentDocs: Map<String, VariableDoc>,
    ): AttributeModel {
        val attrArgs = collectArgs(attribute.value)
        return AttributeModel(
            name = attribute.id.name,
            args = mergeArgs(attrArgs, parentDocs),
            description = "",
        )
    }

    private fun mergeArgs(
        names: Collection<String>,
        docs: Map<String, VariableDoc>,
    ): List<ArgModel> {
        val ordered = linkedSetOf<String>().apply { addAll(names) }
        // Include documented vars even if not referenced (rare, but keep docs).
        docs.keys.forEach { ordered.add(it) }
        return ordered.map { name ->
            val doc = docs[name]
            ArgModel(
                name = name,
                typeHint = doc?.type.orEmpty(),
                description = doc?.description.orEmpty(),
            )
        }
    }

    fun collectArgs(pattern: Pattern): LinkedHashSet<String> {
        val out = linkedSetOf<String>()
        walkPattern(pattern, out)
        return out
    }

    private fun walkPattern(pattern: Pattern, out: MutableSet<String>) {
        for (element in pattern.elements) {
            when (element) {
                is PatternElement.TextElement -> Unit
                is PatternElement.Placeable -> walkExpression(element.expression, out)
            }
        }
    }

    private fun walkExpression(expression: Expression, out: MutableSet<String>) {
        when (expression) {
            is Expression.Select -> {
                walkInline(expression.selector, out)
                for (variant in expression.variants) {
                    walkPattern(variant.value, out)
                }
            }
            is Expression.Inline -> walkInline(expression.expression, out)
        }
    }

    private fun walkInline(expression: InlineExpression, out: MutableSet<String>) {
        when (expression) {
            is InlineExpression.VariableReference -> out.add(expression.id.name)
            is InlineExpression.FunctionReference -> walkArgs(expression.arguments, out)
            is InlineExpression.TermReference -> expression.arguments?.let { walkArgs(it, out) }
            is InlineExpression.Placeable -> walkExpression(expression.expression, out)
            is InlineExpression.StringLiteral,
            is InlineExpression.NumberLiteral,
            is InlineExpression.MessageReference,
            -> Unit
        }
    }

    private fun walkArgs(arguments: CallArguments, out: MutableSet<String>) {
        for (positional in arguments.positional) {
            walkInline(positional, out)
        }
        for (named in arguments.named) {
            walkInline(named.value, out)
        }
    }
}
