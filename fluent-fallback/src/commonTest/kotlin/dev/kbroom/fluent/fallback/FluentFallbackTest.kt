package dev.kbroom.fluent.fallback

import kotlin.test.Test
import kotlin.test.assertEquals

class FluentFallbackTest {
    @Test
    fun testResourceIdCreation() {
        val id = ResourceId("messages", ResourceType.Required)
        assertEquals("messages", id.id)
        assertEquals(ResourceType.Required, id.type)
    }

    @Test
    fun testResourceIdOptional() {
        val id = ResourceId("optional", ResourceType.Optional)
        assertEquals(ResourceType.Optional, id.type)
    }
}
