package dev.kbroom.fluent.testing

import dev.kbroom.fluent.bundle.*
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.fallback.*

/**
 * Test scenarios for localization.
 * Matches Rust fluent-testing scenarios.
 */

data class Query(
    val id: String,
    val args: Map<String, Any?>? = null,
    val expected: String?
)

data class Scenario(
    val name: String,
    val locales: List<LanguageIdentifier>,
    val resources: Map<String, String>,
    val queries: List<Query>,
    val expectedErrors: Int = 0
)

fun simpleScenario(): Scenario {
    return Scenario(
        name = "simple",
        locales = listOf(LanguageIdentifier.parse("en-US")),
        resources = mapOf(
            "en-US" to """
                hello = Hello, World!
                hello-name = Hello, ${'$'}{ name }!
                items = { ${'$'}count -> 
                    [one] { ${'$'}count } item
                   *[other] { ${'$'}count } items
                }
            """.trimIndent()
        ),
        queries = listOf(
            Query("hello", null, "Hello, World!"),
            Query("hello-name", mapOf("name" to "Alice"), "Hello, Alice!"),
            Query("items", mapOf("count" to 1), "1 item"),
            Query("items", mapOf("count" to 5), "5 items")
        )
    )
}

fun browserScenario(): Scenario {
    return Scenario(
        name = "browser",
        locales = listOf(LanguageIdentifier.parse("en-US")),
        resources = mapOf(
            "en-US" to """
                menu-file = File
                menu-edit = Edit
                menu-view = View
                title = Browser
                tabs = { ${'$'}count } tabs
            """.trimIndent()
        ),
        queries = listOf(
            Query("menu-file", null, "File"),
            Query("menu-edit", null, "Edit"),
            Query("title", null, "Browser"),
            Query("tabs", mapOf("count" to 3), "3 tabs")
        )
    )
}

fun emptyResourceOneLocaleScenario(): Scenario {
    return Scenario(
        name = "empty_resource_one_locale",
        locales = listOf(LanguageIdentifier.parse("pl"), LanguageIdentifier.parse("en-US")),
        resources = mapOf("pl" to "", "en-US" to "hello = Hello"),
        queries = listOf(Query("hello", null, "Hello"))
    )
}

fun emptyResourceAllLocalesScenario(): Scenario {
    return Scenario(
        name = "empty_resource_all_locales",
        locales = listOf(LanguageIdentifier.parse("pl"), LanguageIdentifier.parse("en-US")),
        resources = mapOf("pl" to "", "en-US" to ""),
        queries = listOf(Query("hello", null, null)),
        expectedErrors = 1
    )
}

fun getScenarios(): List<Scenario> = listOf(
    simpleScenario(),
    browserScenario(),
    emptyResourceOneLocaleScenario(),
    emptyResourceAllLocalesScenario()
)

fun runScenario(scenario: Scenario): ScenarioResult {
    val errors = mutableListOf<String>()
    val results = mutableListOf<QueryResult>()
    
    for (locale in scenario.locales) {
        try {
            val bundle = FluentBundle(listOf(locale))
            val ftl = scenario.resources[locale.toTag()] ?: ""
            if (ftl.isNotEmpty()) {
                val resource = FluentResource.tryNew(ftl).getOrNull()
                if (resource != null) bundle.addResource(resource)
            }
            bundle.addBuiltins()
            
            for (query in scenario.queries) {
                val args = query.args?.let { map -> 
                    val a = FluentArgs()
                    map.forEach { (k, v) -> a.set(k, v) }
                    a
                }
                val result = bundle.format(query.id, args)
                results.add(QueryResult(query.id, query.expected, result, result == query.expected))
            }
        } catch (e: Exception) {
            errors.add("Error: ${e.message}")
        }
    }
    
    return ScenarioResult(scenario.name, results.all { it.passed } && errors.size == scenario.expectedErrors, results, errors)
}

data class ScenarioResult(val name: String, val passed: Boolean, val results: List<QueryResult>, val errors: List<String>)
data class QueryResult(val id: String, val expected: String?, val actual: String?, val passed: Boolean)
