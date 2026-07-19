package dev.kbroom.fluent.syntax

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.syntax.parser.FluentParser
import dev.kbroom.fluent.testing.syntax.assertAstEquals
import dev.kbroom.fluent.testing.syntax.loadExpectedJson
import dev.kbroom.fluent.testing.syntax.loadSyntaxFixtures
import kotlinx.serialization.json.Json
import kotlin.test.assertTrue

/**
 * Parser fixture tests - compare parsed AST against reference JSON fixtures.
 */
val ParserFixtureTest by testSuite {

    val fixturesDir = "fixtures"

    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    test("parse fixtures no throw") {
        val parser = FluentParser()
        val failures = mutableListOf<String>()

        val fixtures = loadSyntaxFixtures(fixturesDir)
        for ((name, ftlSource) in fixtures) {
            if (name.contains("normalized")) continue
            try {
                parser.parse(ftlSource)
            } catch (e: Throwable) {
                failures.add("$name: ${e.message}")
            }
        }

        val message = if (failures.isEmpty()) {
            ""
        } else {
            "Parser threw on fixtures (${failures.size}):\n${failures.joinToString(
                "\n",
            )}"
        }
        assertTrue(failures.isEmpty(), message)
    }

    test("parse fixtures compare") {
        val failures = mutableListOf<String>()
        val parser = FluentParser()

        val fixtures = loadSyntaxFixtures(fixturesDir)

        for ((name, ftlSource) in fixtures) {
            if (name.contains("normalized")) continue
            val isCrlf = name.contains("crlf")

            try {
                val ast = parser.parse(ftlSource)
                val actualJson = json.encodeToString(ast)
                val expectedJson = loadExpectedJson(name, fixturesDir)
                assertAstEquals(expectedJson, actualJson, isCrlf)
            } catch (e: Throwable) {
                failures.add("$name: ${e.message}")
            }
        }

        val message = if (failures.isEmpty()) {
            ""
        } else {
            "Parser fixture failures (${failures.size}):\n${failures.joinToString(
                "\n",
            )}"
        }
        assertTrue(failures.isEmpty(), message)
    }
}
