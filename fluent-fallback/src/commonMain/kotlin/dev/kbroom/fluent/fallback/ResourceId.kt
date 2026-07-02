package dev.kbroom.fluent.fallback

/**
 * Represents the type of a resource in the fallback chain.
 */
enum class ResourceType {
    /** Resource is required - throws if not found */
    Required,
    /** Resource is optional - silently falls back */
    Optional
}

/**
 * Identifies a resource with its type.
 */
data class ResourceId(
    val id: String,
    val type: ResourceType = ResourceType.Required
)
