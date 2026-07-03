package dev.kbroom.fluent

import dev.kbroom.fluent.bundle.*
import dev.kbroom.fluent.syntax.*
import dev.kbroom.fluent.syntax.parser.*
import dev.kbroom.fluent.intl.*
import dev.kbroom.fluent.fallback.*
import dev.kbroom.fluent.resmgr.*
import dev.kbroom.fluent.pseudo.*

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

// Re-export commonly used types
typealias Fluent = dev.kbroom.fluent.bundle.FluentBundle

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
fun localization(bundle: FluentBundle): Localization<*, *, *> {
    return Localization.simple(bundle)
}

/**
 * Create a Localization from a ResourceManager.
 */
fun localization(
    locales: List<LanguageIdentifier>,
    resourceIds: List<ResourceId>,
    resourceManager: ResourceManager
): Localization<*, *, *> {
    return object : Localization<SimpleGen, SimpleIter, SimpleAsyncIter>(SimpleGen(resourceManager, locales, resourceIds)) {}
}

private class SimpleGen(
    val rm: ResourceManager,
    val locales: List<LanguageIdentifier>,
    val resourceIds: List<ResourceId>
) : BundleGenerator {
    override fun generateBundles(locales: List<LanguageIdentifier>, resourceIds: List<ResourceId>): BundleIterator {
        return SimpleIter(listOf(rm.getBundle(locales, resourceIds)))
    }
    override suspend fun generateBundlesAsync(locales: List<LanguageIdentifier>, resourceIds: List<ResourceId>): AsyncBundleIterator {
        return SimpleAsyncIter(listOf(rm.getBundle(locales, resourceIds)))
    }
}

private class SimpleIter(val bundles: List<FluentBundle>) : BundleIterator {
    private var i = 0
    override fun next(): FluentBundle? = if (i < bundles.size) bundles[i++] else null
}

private class SimpleAsyncIter(val bundles: List<FluentBundle>) : AsyncBundleIterator {
    private var i = 0
    override suspend fun next(): FluentBundle? = if (i < bundles.size) bundles[i++] else null
}

/**
 * Version info.
 */
object Versions {
    const val FLUENT_KT = "0.1.0"
}
