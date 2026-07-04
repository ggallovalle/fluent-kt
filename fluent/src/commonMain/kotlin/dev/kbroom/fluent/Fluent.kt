package dev.kbroom.fluent

import dev.kbroom.fluent.bundle.FluentArgs
import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.fluentArgsOf
import dev.kbroom.fluent.fallback.BundleGenerator
import dev.kbroom.fluent.fallback.BundleIterator
import dev.kbroom.fluent.fallback.SimpleLocalization
import dev.kbroom.fluent.fallback.ResourceId
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.pseudo.PseudoLocale
import dev.kbroom.fluent.resmgr.ResourceManager
import dev.kbroom.fluent.syntax.Resource
import dev.kbroom.fluent.syntax.parser.FluentParser

/**
 * Fluent-KT: Kotlin implementation of Project Fluent localization system.
 * 
 * This is the main entry point for the fluent localization library.
 * 
 * ## Quick Start
 * 
 * ```kotlin
 * import dev.kbroom.fluent.*
 * 
 * // Create a bundle
 * val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en-US")))
 * 
 * // Add resources
 * val resource = FluentResource.tryNew("hello = Hello, { $name }!").getOrThrow()
 * bundle.addResource(resource)
 * bundle.addBuiltins()
 * 
 * // Format a message
 * val args = fluentArgsOf("name" to "World")
 * val result = bundle.format("hello", args)
 * println(result) // "Hello, World!"
 * ```
 */

/**
 * Convenience function to parse an FTL string into a Resource.
 */
fun parseFtl(source: String): Result<FluentResource> = FluentResource.tryNew(source)

/**
 * Convenience function to create a FluentBundle with defaults.
 */
fun fluentBundle(locales: List<LanguageIdentifier>): FluentBundle = FluentBundle(locales)

/**
 * Create FluentArgs from key-value pairs.
 */
fun fluentArgs(vararg pairs: Pair<String, Any?>): FluentArgs = fluentArgsOf(*pairs)

/**
 * Convenience extension to format with args.
 */
fun FluentBundle.formatWith(id: String, vararg args: Pair<String, Any?>): String? {
    return this.format(id, fluentArgsOf(*args))
}

/**
 * Create a Localization from a single bundle.
 */
fun localization(bundle: FluentBundle): SimpleLocalization {
    return SimpleLocalization(bundle)
}

/**
 * Version info.
 */
object Versions {
    const val FLUENT_KT = "0.1.0"
}
