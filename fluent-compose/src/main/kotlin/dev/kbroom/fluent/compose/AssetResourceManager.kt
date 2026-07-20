package dev.kbroom.fluent.compose

import android.content.res.AssetManager
import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.fluentBundle
import dev.kbroom.fluent.fallback.ResourceId
import dev.kbroom.fluent.intl.LanguageIdentifier
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Loads Fluent FTL resources from Android [AssetManager].
 *
 * Path negotiation (same order as fluent-resmgr):
 * 1. `{basePath}/{locale-tag}/{resourceId}.ftl`
 * 2. `{basePath}/{language}/{resourceId}.ftl`
 * 3. `{basePath}/{resourceId}.ftl`
 */
class AssetResourceManager(private val assets: AssetManager, private val basePath: String = "i18n") {
    fun loadBundle(
        locale: LanguageIdentifier,
        resourceIds: List<ResourceId>,
    ): FluentBundle =
        fluentBundle(listOf(locale)) {
            resourceIds
                .mapNotNull { resourceId -> loadResource(locale, resourceId) }
                .mapNotNull { content -> FluentResource.tryNew(content).getOrNull() }
                .forEach { resource -> addResourceOverriding(resource) }
            addBuiltins()
        }

    fun loadRegistry(
        locale: LanguageIdentifier,
        resourceIdsByBundle: Map<String, List<ResourceId>>,
        fallbackLocale: LanguageIdentifier,
    ): FluentBundleRegistry {
        val bundles = linkedMapOf<String, FluentBundle>()
        for ((bundleName, resourceIds) in resourceIdsByBundle) {
            val hasPrimary = resourceIds.any { loadResource(locale, it) != null }
            val effectiveLocale = if (hasPrimary || locale == fallbackLocale) {
                locale
            } else {
                fallbackLocale
            }
            bundles[bundleName] = loadBundle(effectiveLocale, resourceIds)
        }
        return FluentBundleRegistry(bundles)
    }

    internal fun loadResource(locale: LanguageIdentifier, resourceId: ResourceId): String? {
        val fileName = "${resourceId.id}.ftl"
        val candidates = listOf(
            "$basePath/${locale.toTag()}/$fileName",
            "$basePath/${locale.language}/$fileName",
            "$basePath/$fileName",
        )
        return candidates.firstNotNullOfOrNull { path -> readAsset(path) }
    }

    private fun readAsset(path: String): String? =
        try {
            assets.open(path).use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
        } catch (_: IOException) {
            null
        }
}
