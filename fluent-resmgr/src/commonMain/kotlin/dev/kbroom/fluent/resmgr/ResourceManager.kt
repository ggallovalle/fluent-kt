package dev.kbroom.fluent.resmgr

import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.fluentBundle
import dev.kbroom.fluent.fallback.ResourceId
import dev.kbroom.fluent.intl.LanguageIdentifier

/**
 * Resource Manager for loading Fluent bundles from files.
 */
open class ResourceManager(private val basePath: String) {

    fun getBundle(locales: List<LanguageIdentifier>, resourceIds: List<ResourceId>): FluentBundle =
        fluentBundle(locales) {
            for (resourceId in resourceIds) {
                val content = loadResource(locales.first(), resourceId)
                if (content != null) {
                    val resource = FluentResource.tryNew(content).getOrNull()
                    if (resource != null) addResourceOverriding(resource)
                }
            }
            addBuiltins()
        }

    fun getBundles(
        locales: List<LanguageIdentifier>,
        resourceIds: List<ResourceId>,
    ): Map<LanguageIdentifier, FluentBundle> {
        val result = linkedMapOf<LanguageIdentifier, FluentBundle>()

        for (locale in locales) {
            val bundle = fluentBundle(listOf(locale)) {
                for (resourceId in resourceIds) {
                    val content = loadResource(locale, resourceId)
                    if (content != null) {
                        val resource = FluentResource.tryNew(content).getOrNull()
                        if (resource != null) addResourceOverriding(resource)
                    }
                }
                addBuiltins()
            }
            result[locale] = bundle
        }

        return result
    }

    @Suppress("SwallowedException")
    internal open fun loadResource(locale: LanguageIdentifier, resourceId: ResourceId): String? {
        val fileName = "${resourceId.id}.ftl"
        val paths = listOf(
            "$basePath/${locale.toTag()}/$fileName",
            "$basePath/${locale.language}/$fileName",
            "$basePath/$fileName",
        )

        for (path in paths) {
            try {
                val content = readFile(path)
                if (content != null) return content
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Continue trying the next path. The original exception
                // is intentionally discarded — fallback lookup is the
                // contract here; if every path fails we return null.
            }
        }
        return null
    }

    internal open fun readFile(path: String): String? = readFileImpl(path)
}

/**
 * Callback-based async resource manager.
 */
class CallbackResourceManager(private val basePath: String) {

    interface Callback {
        fun onSuccess(bundle: FluentBundle)
        fun onError(error: Exception)
    }

    fun getBundle(locales: List<LanguageIdentifier>, resourceIds: List<ResourceId>, callback: Callback) {
        try {
            val bundle = loadBundle(locales, resourceIds)
            callback.onSuccess(bundle)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            callback.onError(e)
        }
    }

    private fun loadBundle(locales: List<LanguageIdentifier>, resourceIds: List<ResourceId>): FluentBundle =
        fluentBundle(locales) {
            for (resourceId in resourceIds) {
                val content = loadResource(locales.first(), resourceId)
                if (content != null) {
                    val resource = FluentResource.tryNew(content).getOrNull()
                    if (resource != null) addResourceOverriding(resource)
                }
            }
            addBuiltins()
        }

    @Suppress("SwallowedException")
    private fun loadResource(locale: LanguageIdentifier, resourceId: ResourceId): String? {
        val fileName = "${resourceId.id}.ftl"
        val paths = listOf(
            "$basePath/${locale.toTag()}/$fileName",
            "$basePath/${locale.language}/$fileName",
            "$basePath/$fileName",
        )
        for (path in paths) {
            try {
                val content = readFile(path)
                if (content != null) return content
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Continue trying the next path. The original exception
                // is intentionally discarded — fallback lookup is the
                // contract here; if every path fails we return null.
            }
        }
        return null
    }

    internal open fun readFile(path: String): String? = readFileImpl(path)
}

/**
 * Platform-specific file reading implementation.
 */
expect fun readFileImpl(path: String): String?
