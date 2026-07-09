package dev.kbroom.fluent.fallback

import kotlin.test.assertEquals
import de.infix.testBalloon.framework.core.testSuite

val FluentFallbackTest by testSuite {
    test("resource id creation") {
        val id = ResourceId("messages", ResourceType.Required)
        assertEquals("messages", id.id)
        assertEquals(ResourceType.Required, id.type)
    }

    test("resource id optional") {
        val id = ResourceId("optional", ResourceType.Optional)
        assertEquals(ResourceType.Optional, id.type)
    }
}
