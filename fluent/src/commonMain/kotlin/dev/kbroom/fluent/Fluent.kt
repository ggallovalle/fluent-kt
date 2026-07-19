package dev.kbroom.fluent

import dev.kbroom.fluent.bundle.FluentArgs
import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.fluentArgsOf
import dev.kbroom.fluent.fallback.SimpleLocalization
import dev.kbroom.fluent.intl.LanguageIdentifier

/**
 * Convenience function to parse an FTL string into a Resource.
 *
 * ## Quick Start
 *
 * ```kotlin
 * import dev.kbroom.fluent.*
 *
 * // Create a bundle via the DSL
 * val bundle = fluentBundle(locales = listOf(LanguageIdentifier.parse("en"))) {
 *     resource("hello = Hello, { $name }!")
 *     builtins()
 * }
 *
 * // Format
 * val args = fluentArgsOf("name" to "World")
 * val greeting = bundle.format("hello", args)
 * println(greeting) // "Hello, World!"
 * ```
 */
fun parseFtl(source: String): Result<FluentResource> = FluentResource.tryNew(source)

/**
 * Convenience function to create a FluentBundle with defaults.
 *
 * Equivalent to [fluentBundle] in the `fluent-bundle` module — re-exported
 * here for users who want a single import.
 */
fun fluentBundle(locales: List<LanguageIdentifier>): FluentBundle = dev.kbroom.fluent.bundle.fluentBundle(locales)

/**
 * Create FluentArgs from key-value pairs.
 */
fun fluentArgs(vararg pairs: Pair<String, Any?>): FluentArgs = fluentArgsOf(*pairs)

/**
 * Convenience extension to format with args.
 */
fun FluentBundle.formatWith(id: String, vararg args: Pair<String, Any?>): String? = this.format(id, fluentArgsOf(*args))

/**
 * Create a Localization from a single bundle.
 */
fun localization(bundle: FluentBundle): SimpleLocalization = SimpleLocalization(bundle)

/**
 * Version info.
 */
object Versions {
    const val FLUENT_KT = "0.1.0"
}
