package dev.kbroom.fluent.resmgr

import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.FluentArgs
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.fallback.ResourceId
import dev.kbroom.fluent.fallback.ResourceType

/**
 * Resource Manager for loading Fluent bundles from files.
 */
class ResourceManager(private val basePath: String) {
    
    /**
     * Get a bundle for the given locales and resource IDs.
     */
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
    
    /**
     * Load a resource file.
     */
    private fun loadResource(locale: LanguageIdentifier, resourceId: ResourceId): String? {
        val fileName = "${resourceId.id}.ftl"
        
        // Try language-specific path
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
                // Continue to next path
            }
        }
        
        return null
    }
    
    /**
     * Read file - to be implemented with platform-specific code.
     */
    protected open fun readFile(path: String): String? {
        // Default implementation using JVM
        return try {
            java.io.File(path).readText()
        } catch (e: Exception) {
            null
        }
    }
}
