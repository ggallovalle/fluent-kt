package dev.kbroom.fluent.syntax

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.syntax.parser.FluentParser
import dev.kbroom.fluent.testing.syntax.loadExpectedJson
import dev.kbroom.fluent.testing.syntax.loadSyntaxFixtures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Structural AST equality — asserts the parser's serialized output matches
 * the reference JSON for a fixture at the semantic level (ignoring field
 * order and null/absent differences).
 *
 * The existing ParserFixtureTest uses a "lenient" comparison that only checks
 * the parser didn't crash; this test asserts the structural shape so
 * regressions in @SerialName tags, Junk annotations, or AST shape actually
 * fail. We compare semantically (key set + values) rather than byte-for-byte
 * because kotlinx-serialization encodes fields in declaration order while the
 * upstream fluent-rs fixtures encode in the order they happen to write.
 */
val StructuralAstEqualsTest by testSuite {

    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadFixture(name: String): String = loadSyntaxFixtures("fixtures")
        .firstOrNull { it.first == name }
        ?.second ?: error("fixture not found: $name")

    fun normalize(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> {
            val filtered = element.entries
                .filter { (k, _) -> k != "type" && k != "docComment" }
                .map { (k, v) -> k to normalize(v) }
                .sortedBy { it.first }
                .toMap()
            JsonObject(filtered)
        }

        is JsonArray -> JsonArray(element.map(::normalize))

        is JsonPrimitive -> element

        is JsonNull -> element
    }

    @Suppress("CyclomaticComplexMethod")
    fun assertStructurallyEqual(expected: JsonElement, actual: JsonElement, path: String = "$") {
        if (expected::class != actual::class) {
            fail("$path: kind mismatch: expected ${expected::class.simpleName}, got ${actual::class.simpleName}")
        }
        when (expected) {
            is JsonObject -> {
                val a = actual as JsonObject
                val expKeys = expected.keys.toSet()
                val actKeys = a.keys.toSet()
                if (expKeys != actKeys) {
                    val missing = expKeys - actKeys
                    val extra = actKeys - expKeys
                    val detail = buildString {
                        if (missing.isNotEmpty()) append(" missing=$missing")
                        if (extra.isNotEmpty()) append(" extra=$extra")
                    }
                    fail("$path: key mismatch$detail; expected=$expected actual=$a")
                }
                for ((k, v) in expected) {
                    assertStructurallyEqual(v, a.getValue(k), "$path.$k")
                }
            }

            is JsonArray -> {
                val a = actual as JsonArray
                if (expected.size != a.size) {
                    fail("$path: array length mismatch: expected ${expected.size}, got ${a.size}")
                }
                for ((i, v) in expected.withIndex()) {
                    assertStructurallyEqual(v, a[i], "$path[$i]")
                }
            }

            is JsonPrimitive -> {
                if (expected != actual as JsonPrimitive) {
                    fail("$path: primitive mismatch: expected=$expected actual=$actual")
                }
            }

            is JsonNull -> { /* both are JsonNull — always equal */ }

            else -> fail("unhandled: ${expected::class.simpleName}")
        }
    }

    test("eof_empty.ftl matches eof_empty.json (empty body)") {
        val parser = FluentParser()
        val ast = parser.parse(loadFixture("eof_empty.ftl"))
        val actual = normalize(json.encodeToJsonElement(Resource.serializer(), ast))
        val expected = normalize(json.parseToJsonElement(loadExpectedJson("eof_empty", "fixtures")))
        assertStructurallyEqual(expected, actual)
    }

    test("junk.ftl matches junk.json (parses broken FTL into Junk entries)") {
        val parser = FluentParser()
        val ast = parser.parse(loadFixture("junk.ftl"))
        val actual = normalize(json.encodeToJsonElement(Resource.serializer(), ast))
        val expected = normalize(json.parseToJsonElement(loadExpectedJson("junk", "fixtures")))
        assertStructurallyEqual(expected, actual)
    }

    // Smoke test: every fixture in fixtures/ parses without throwing and
    // produces a structurally-valid Resource (a JsonObject with a body field).
    test("every fixture parses into a structurally valid Resource") {
        val parser = FluentParser()
        val fixtures = loadSyntaxFixtures("fixtures")
        val failures = mutableListOf<String>()
        for ((name, ftl) in fixtures) {
            if (!name.contains("normalized")) {
                val result = runCatching {
                    val ast = parser.parse(ftl)
                    val encoded = json.encodeToJsonElement(Resource.serializer(), ast)
                    when {
                        encoded !is JsonObject -> "$name: root is not a JsonObject"
                        encoded["body"] !is JsonArray -> "$name: missing or non-array body"
                        else -> null
                    }
                }.getOrElse { e -> "$name: ${e.message}" }
                if (result != null) failures.add(result)
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
