package dev.kbroom.fluent.testing.syntax

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Minimal fixture model for loading JSON fixtures and comparing with parsed output.
 */
@Serializable
data class FixtureResource(
    val type: String = "Resource",
    val body: List<JsonElement> = emptyList()
)
