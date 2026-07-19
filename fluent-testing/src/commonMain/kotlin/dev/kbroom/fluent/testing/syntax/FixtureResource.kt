package dev.kbroom.fluent.testing.syntax

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Minimal fixture model for loading JSON fixtures and comparing with parsed output.
 */
@Serializable
data class FixtureResource(val type: String = "Resource", val body: List<JsonElement> = emptyList())
