package dev.kbroom.fluent.fallback

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

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
