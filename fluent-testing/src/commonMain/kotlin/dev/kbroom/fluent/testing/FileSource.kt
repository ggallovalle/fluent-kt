package dev.kbroom.fluent.testing

import dev.kbroom.fluent.fallback.ResourceId
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.resmgr.ResourceManager

/**
 * FileSource - represents a directory of FTL files organized by locale.
 * Matches Rust fluent-testing FileSource.
 */
data class FileSource(val name: String, val path: String, val locales: List<String>) {
    fun getPathForLocale(locale: String): String = path.replace("{locale}", locale)

    fun localesAsIdentifiers(): List<LanguageIdentifier> = locales.map { LanguageIdentifier.parse(it) }

    fun hasLocale(locale: String): Boolean = locale in locales
}

enum class ResourceType { Required, Optional }

data class ResourceIdWithType(val id: String, val type: ResourceType = ResourceType.Required) {
    companion object {
        fun required(id: String) = ResourceIdWithType(id, ResourceType.Required)
        fun optional(id: String) = ResourceIdWithType(id, ResourceType.Optional)
    }
}

fun String.toResourceId(type: ResourceType = ResourceType.Required): ResourceIdWithType = ResourceIdWithType(this, type)

class TestEnvironment(private val basePath: String, private val fileSources: List<FileSource>) {
    private val resourceManager = ResourceManager(basePath)

    fun getBundles(
        locales: List<LanguageIdentifier>,
        resourceIds: List<ResourceIdWithType>,
    ): Map<LanguageIdentifier, dev.kbroom.fluent.bundle.FluentBundle> = resourceManager.getBundles(
        locales,
        resourceIds.map { ResourceId(it.id) },
    )

    fun getBundle(
        locales: List<LanguageIdentifier>,
        resourceIds: List<ResourceIdWithType>,
    ): dev.kbroom.fluent.bundle.FluentBundle = resourceManager.getBundle(locales, resourceIds.map { ResourceId(it.id) })
}

fun fileSource(name: String, path: String, locales: List<String>) = FileSource(name, path, locales)
