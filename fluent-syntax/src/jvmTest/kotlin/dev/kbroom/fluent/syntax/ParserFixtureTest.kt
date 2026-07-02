package dev.kbroom.fluent.syntax

import dev.kbroom.fluent.testing.syntax.*
import dev.kbroom.fluent.syntax.parser.FluentParser
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.fail

/**
 * Parser fixture tests - compare parsed AST against reference JSON fixtures.
 */
class ParserFixtureTest {
    
    private val fixturesDir = "fixtures"
    
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun parseFixturesNoThrow() {
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
        
        if (failures.isNotEmpty()) {
            fail("Parser threw on fixtures (${failures.size}):\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun parseFixturesCompare() {
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
        
        if (failures.isNotEmpty()) {
            fail("Parser fixture failures (${failures.size}):\n${failures.joinToString("\n")}")
        }
    }
}
