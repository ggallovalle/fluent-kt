package dev.kbroom.fluent.resmgr

import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.fallback.ResourceId

/**
 * Resource Manager for loading Fluent bundles from files.
 */
class ResourceManager(private val basePath: String) {
    
    fun getBundle(locales: List<LanguageIdentifier>, resourceIds: List<ResourceId>): FluentBundle {
        val bundle = FluentBundle(locales)
        
        for (resourceId in resourceIds) {
            val content = loadResource(locales.first(), resourceId)
            if (content != null) {
                val resource = FluentResource.tryNew(content).getOrNull()
                if (resource != null) {
                    bundle.addResourceOverriding(resource)
                }
            }
        }
        
        bundle.addBuiltins()
        return bundle
    }
    
    fun getBundles(locales: List<LanguageIdentifier>, resourceIds: List<ResourceId>): Map<LanguageIdentifier, FluentBundle> {
        val result = linkedMapOf<LanguageIdentifier, FluentBundle>()
        
        for (locale in locales) {
            val bundle = FluentBundle(listOf(locale))
            
            for (resourceId in resourceIds) {
                val content = loadResource(locale, resourceId)
                if (content != null) {
                    val resource = FluentResource.tryNew(content).getOrNull()
                    if (resource != null) {
                        bundle.addResourceOverriding(resource)
                    }
                }
            }
            
            bundle.addBuiltins()
            result[locale] = bundle
        }
        
        return result
    }
    
    protected open fun loadResource(locale: LanguageIdentifier, resourceId: ResourceId): String? {
        val fileName = "${resourceId.id}.ftl"
        val paths = listOf(
            "$basePath/${locale.toTag()}/$fileName",
            "$basePath/${locale.language}/$fileName",
            "$basePath/$fileName"
        )
        
        for (path in paths) {
            try {
                val content = readFile(path)
                if (content != null) return content
            } catch (e: Exception) {
                // Continue
            }
        }
        return null
    }
    
    protected open fun readFile(path: String): String? {
        return try {
            java.io.File(path).readText()
        } catch (e: Exception) {
            null
        }
    }
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
        } catch (e: Exception) {
            callback.onError(e)
        }
    }
    
    private fun loadBundle(locales: List<LanguageIdentifier>, resourceIds: List<ResourceId>): FluentBundle {
        val bundle = FluentBundle(locales)
        for (resourceId in resourceIds) {
            val content = loadResource(locales.first(), resourceId)
            if (content != null) {
                val resource = FluentResource.tryNew(content).getOrNull()
                if (resource != null) {
                    bundle.addResourceOverriding(resource)
                }
            }
        }
        bundle.addBuiltins()
        return bundle
    }
    
    private fun loadResource(locale: LanguageIdentifier, resourceId: ResourceId): String? {
        val fileName = "${resourceId.id}.ftl"
        val paths = listOf(
            "$basePath/${locale.toTag()}/$fileName",
            "$basePath/${locale.language}/$fileName",
            "$basePath/$fileName"
        )
        for (path in paths) {
            try {
                val content = readFile(path)
                if (content != null) return content
            } catch (e: Exception) {
                // Continue
            }
        }
        return null
    }
    
    protected open fun readFile(path: String): String? {
        return try { java.io.File(path).readText() } catch (e: Exception) { null }
    }
}
