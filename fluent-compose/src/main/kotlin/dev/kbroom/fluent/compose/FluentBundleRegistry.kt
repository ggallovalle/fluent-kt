package dev.kbroom.fluent.compose

import dev.kbroom.fluent.bundle.FluentBundle

/**
 * Multi-bundle registry exposed via [LocalFluentBundles].
 */
class FluentBundleRegistry(private val bundles: Map<String, FluentBundle>) {
    fun get(bundle: String): FluentBundle =
        bundles[bundle]
            ?: error("No FluentBundle registered for '$bundle'. Known: ${bundles.keys}")

    fun contains(bundle: String): Boolean = bundle in bundles

    fun names(): Set<String> = bundles.keys
}
