package dev.kbroom.fluent.syntax

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.syntax.parser.FluentParser
import dev.kbroom.fluent.syntax.serializer.SerializerOptions
import dev.kbroom.fluent.syntax.serializer.serialize
import dev.kbroom.fluent.testing.syntax.loadSyntaxFixtures
import kotlinx.serialization.json.Json
import kotlin.test.assertTrue

/**
 * Round-trip parse -> serialize -> parse across a curated subset of
 * fixtures whose ASTs the serializer handles correctly today. Each
 * roundTripFixtures entry states the case the test guards. Failures
 * land here rather than in the lenient assertAstEquals path so
 * regressions show up immediately.
 */
val SerializerRoundTripTest by testSuite {

    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val roundTripFixtures = listOf(
        "roundtrip_simple.ftl" to "simple Message",
        "roundtrip_two_messages.ftl" to "two Messages separated by newline",
        "roundtrip_leading_comment.ftl" to "GroupComment followed by Message",
    )

    test("structural round trip across core fixtures") {
        val parser = FluentParser()
        val opts = SerializerOptions(withJunk = true)
        val failures = mutableListOf<String>()

        val sourceMap = loadSyntaxFixtures("fixtures").toMap()
        for ((name, description) in roundTripFixtures) {
            val source = sourceMap[name]
                ?: error("Missing fixture $name — add it to commonTest/resources/fixtures/")
            try {
                val r1 = parser.parse(source)
                val ftln = serialize(r1, opts)
                val r2 = parser.parse(ftln)
                val j1 = json.encodeToJsonElement(Resource.serializer(), r1)
                val j2 = json.encodeToJsonElement(Resource.serializer(), r2)
                if (j1 != j2) {
                    failures += "$name ($description): round-trip diverged"
                }
            } catch (e: Throwable) {
                failures += "$name ($description): ${e.message}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
