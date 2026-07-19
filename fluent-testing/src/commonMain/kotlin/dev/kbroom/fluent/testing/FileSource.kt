package dev.kbroom.fluent.testing

import dev.kbroom.fluent.intl.LanguageIdentifier

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

fun fileSource(name: String, path: String, locales: List<String>) = FileSource(name, path, locales)
