package dev.kbroom.fluent.codegen.scaffold

import dev.kbroom.fluent.codegen.discovery.LayoutDiscovery
import dev.kbroom.fluent.codegen.model.FluentLayout
import dev.kbroom.fluent.codegen.model.ScaffoldMode
import dev.kbroom.fluent.codegen.model.ScaffoldOptions
import dev.kbroom.fluent.codegen.model.ScaffoldReport
import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.Expression
import dev.kbroom.fluent.syntax.InlineExpression
import dev.kbroom.fluent.syntax.Pattern
import dev.kbroom.fluent.syntax.PatternElement
import dev.kbroom.fluent.syntax.Resource
import dev.kbroom.fluent.syntax.Variant
import dev.kbroom.fluent.syntax.parser.FluentParser
import dev.kbroom.fluent.syntax.serializer.Serializer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

/**
 * Creates a new locale tree by rewriting FTL files from the default locale.
 */
object LocaleScaffolder {
    fun scaffold(
        root: Path,
        layout: FluentLayout,
        fromLocale: String,
        toLocale: String,
        options: ScaffoldOptions = ScaffoldOptions(),
    ): ScaffoldReport {
        require(fromLocale != toLocale) { "from and to locales must differ" }
        val tree = LayoutDiscovery.discover(root, layout)
        val sourceFiles = tree.files.filter { it.locale == fromLocale }
        if (sourceFiles.isEmpty()) {
            return ScaffoldReport(
                created = emptyList(),
                skipped = emptyList(),
                failed = listOf("*" to "No FTL files found for locale '$fromLocale' under $root"),
            )
        }

        val created = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()
        val serializer = Serializer()

        for (file in sourceFiles) {
            val target = targetPath(root, layout, fromLocale, toLocale, file.absolutePath)
            val relative = target.relativeTo(root).invariantSeparatorsPathString
            try {
                if (target.exists() && !options.overwrite) {
                    skipped += relative
                    continue
                }
                val sourceText = Files.readString(file.absolutePath)
                val output = rewrite(sourceText, toLocale, options.mode, serializer)
                target.parent?.createDirectories()
                Files.writeString(target, output)
                created += relative
            } catch (ex: IOException) {
                failed.add(relative to (ex.message ?: ex::class.simpleName.orEmpty()))
            } catch (ex: IllegalStateException) {
                failed.add(relative to (ex.message ?: ex::class.simpleName.orEmpty()))
            } catch (ex: IllegalArgumentException) {
                failed.add(relative to (ex.message ?: ex::class.simpleName.orEmpty()))
            }
        }

        return ScaffoldReport(created, skipped, failed)
    }

    internal fun rewrite(
        source: String,
        toLocale: String,
        mode: ScaffoldMode,
        serializer: Serializer = Serializer(),
    ): String {
        val parser = FluentParser()
        val resource = parser.parse(source)
        val junk = resource.body.filterIsInstance<Entry.Junk>()
        check(junk.isEmpty()) {
            "Cannot scaffold file with Junk entries: ${junk.first().content.take(60)}"
        }
        val rewritten = when (mode) {
            ScaffoldMode.CopyAsPlaceholder -> resource
            ScaffoldMode.StructureOnly -> rewriteResource(resource) { "TODO" }
            ScaffoldMode.PseudoPrefix -> rewriteResource(resource) { text ->
                if (text.isBlank()) text else "[$toLocale] $text"
            }
        }
        return serializer.serialize(rewritten)
    }

    private fun targetPath(
        root: Path,
        layout: FluentLayout,
        fromLocale: String,
        toLocale: String,
        sourceFile: Path,
    ): Path {
        val relative = sourceFile.relativeTo(root).invariantSeparatorsPathString
        val replaced = when (layout) {
            FluentLayout.LocaleBundle, FluentLayout.FlatLocale -> {
                // first segment is locale
                val parts = relative.split('/', limit = 2)
                check(parts.size == 2 && parts[0] == fromLocale) {
                    "Unexpected path $relative for locale $fromLocale"
                }
                "$toLocale/${parts[1]}"
            }
            FluentLayout.BundleLocale -> {
                // {bundle}/{locale}/...
                val parts = relative.split('/')
                check(parts.size >= 2 && parts[1] == fromLocale) {
                    "Unexpected path $relative for locale $fromLocale"
                }
                parts.toMutableList().also { it[1] = toLocale }.joinToString("/")
            }
        }
        return root.resolve(replaced)
    }

    private fun rewriteResource(resource: Resource, textTransform: (String) -> String): Resource =
        Resource(
            body = resource.body.map { entry ->
                when (entry) {
                    is Entry.Message -> entry.copy(
                        value = entry.value?.let { rewritePattern(it, textTransform) },
                        attributes = entry.attributes.map { attr ->
                            attr.copy(value = rewritePattern(attr.value, textTransform))
                        },
                    )
                    is Entry.Term -> entry.copy(
                        value = rewritePattern(entry.value, textTransform),
                        attributes = entry.attributes.map { attr ->
                            attr.copy(value = rewritePattern(attr.value, textTransform))
                        },
                    )
                    else -> entry
                }
            },
        )

    private fun rewritePattern(pattern: Pattern, textTransform: (String) -> String): Pattern =
        Pattern(
            elements = pattern.elements.map { element ->
                when (element) {
                    is PatternElement.TextElement ->
                        PatternElement.TextElement(textTransform(element.value))
                    is PatternElement.Placeable ->
                        PatternElement.Placeable(rewriteExpression(element.expression, textTransform))
                }
            },
        )

    private fun rewriteExpression(
        expression: Expression,
        textTransform: (String) -> String,
    ): Expression = when (expression) {
        is Expression.Select -> Expression.Select(
            selector = rewriteInline(expression.selector, textTransform),
            variants = expression.variants.map { variant ->
                Variant(
                    key = variant.key,
                    value = rewritePattern(variant.value, textTransform),
                    default = variant.default,
                )
            },
        )
        is Expression.Inline -> Expression.Inline(rewriteInline(expression.expression, textTransform))
    }

    private fun rewriteInline(
        expression: InlineExpression,
        textTransform: (String) -> String,
    ): InlineExpression = when (expression) {
        is InlineExpression.Placeable ->
            InlineExpression.Placeable(rewriteExpression(expression.expression, textTransform))
        else -> expression
    }
}
