package dev.kbroom.fluent.testing

import dev.kbroom.fluent.bundle.FluentArgs
import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.intl.LanguageIdentifier

/**
 * Test scenarios for localization.
 * Matches Rust fluent-testing scenarios.
 */

data class Query(val id: String, val args: Map<String, Any?>? = null, val expected: String?)

data class Scenario(
    val name: String,
    val locales: List<LanguageIdentifier>,
    val resources: Map<String, String>,
    val queries: List<Query>,
    val expectedErrors: Int = 0,
)

fun simpleScenario(): Scenario = Scenario(
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
        """.trimIndent(),
    ),
    queries = listOf(
        Query("hello", null, "Hello, World!"),
        Query("hello-name", mapOf("name" to "Alice"), "Hello, Alice!"),
        Query("items", mapOf("count" to 1), "1 item"),
        Query("items", mapOf("count" to 5), "5 items"),
    ),
)

fun browserScenario(): Scenario = Scenario(
    name = "browser",
    locales = listOf(LanguageIdentifier.parse("en-US")),
    resources = mapOf(
        "en-US" to """
                menu-file = File
                menu-edit = Edit
                menu-view = View
                title = Browser
                tabs = { ${'$'}count } tabs
        """.trimIndent(),
    ),
    queries = listOf(
        Query("menu-file", null, "File"),
        Query("menu-edit", null, "Edit"),
        Query("title", null, "Browser"),
        Query("tabs", mapOf("count" to 3), "3 tabs"),
    ),
)

fun emptyResourceOneLocaleScenario(): Scenario = Scenario(
    name = "empty_resource_one_locale",
    locales = listOf(LanguageIdentifier.parse("pl"), LanguageIdentifier.parse("en-US")),
    resources = mapOf("pl" to "", "en-US" to "hello = Hello"),
    queries = listOf(Query("hello", null, "Hello")),
)

fun emptyResourceAllLocalesScenario(): Scenario = Scenario(
    name = "empty_resource_all_locales",
    locales = listOf(LanguageIdentifier.parse("pl"), LanguageIdentifier.parse("en-US")),
    resources = mapOf("pl" to "", "en-US" to ""),
    queries = listOf(Query("hello", null, null)),
    expectedErrors = 1,
)

/**
 * Missing optional resource in one locale - should fallback only for that resource.
 */
fun missingOptionalOneLocaleScenario(): Scenario = Scenario(
    name = "missing_optional_one_locale",
    locales = listOf(LanguageIdentifier.parse("en-US"), LanguageIdentifier.parse("pl")),
    resources = mapOf(
        "en-US" to """
                history-section-label = History
                present-key = Present in en-US
        """.trimIndent(),
        "pl" to """
                history-section-label = Historia
        """.trimIndent(),
    ),
    queries = listOf(
        Query("history-section-label", null, "Historia"), // fallback to pl
        Query("present-key", null, "Present in en-US"), // use en-US
    ),
)

/**
 * Missing optional resource in all locales - should fallback completely.
 */
fun missingOptionalAllLocalesScenario(): Scenario = Scenario(
    name = "missing_optional_all_locales",
    locales = listOf(LanguageIdentifier.parse("en-US"), LanguageIdentifier.parse("pl")),
    resources = mapOf(
        "en-US" to """
                present-key = Present
        """.trimIndent(),
        "pl" to """
                present-key = Obecny
        """.trimIndent(),
    ),
    queries = listOf(
        Query("present-key", null, "Obecny"), // use pl
        Query("missing-key", null, null), // missing, optional
    ),
)

/**
 * Missing required resource in one locale - should error.
 */
fun missingRequiredOneLocaleScenario(): Scenario = Scenario(
    name = "missing_required_one_locale",
    locales = listOf(LanguageIdentifier.parse("en-US"), LanguageIdentifier.parse("pl")),
    resources = mapOf(
        "en-US" to """
                required-key = Required
        """.trimIndent(),
        // pl has no resources - required
    ),
    queries = listOf(
        Query("required-key", null, "Required"),
    ),
    expectedErrors = 1,
)

/**
 * Missing required resource in all locales - should error.
 */
fun missingRequiredAllLocalesScenario(): Scenario = Scenario(
    name = "missing_required_all_locales",
    locales = listOf(LanguageIdentifier.parse("en-US"), LanguageIdentifier.parse("pl")),
    resources = mapOf(
        "en-US" to "",
        "pl" to "",
    ),
    queries = listOf(
        Query("any-key", null, null),
    ),
    expectedErrors = 1,
)

fun getScenarios(): List<Scenario> = listOf(
    simpleScenario(),
    browserScenario(),
    emptyResourceOneLocaleScenario(),
    emptyResourceAllLocalesScenario(),
    missingOptionalOneLocaleScenario(),
    missingOptionalAllLocalesScenario(),
    missingRequiredOneLocaleScenario(),
    missingRequiredAllLocalesScenario(),
)

fun runScenario(scenario: Scenario): ScenarioResult {
    val errors = mutableListOf<String>()
    val results = mutableListOf<QueryResult>()

    for (locale in scenario.locales) {
        runLocaleScenario(scenario, locale, results, errors)
    }

    return ScenarioResult(
        scenario.name,
        results.all { it.passed } && errors.size == scenario.expectedErrors,
        results,
        errors,
    )
}

private fun runLocaleScenario(
    scenario: Scenario,
    locale: LanguageIdentifier,
    results: MutableList<QueryResult>,
    errors: MutableList<String>,
) {
    try {
        val bundle = buildBundleForScenario(scenario, locale)
        for (query in scenario.queries) {
            val result = runQuery(bundle, query)
            results.add(result)
        }
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        errors.add("Error: ${e.message}")
    }
}

private fun buildBundleForScenario(scenario: Scenario, locale: LanguageIdentifier): FluentBundle {
    val bundle = FluentBundle(listOf(locale))
    val ftl = scenario.resources[locale.toTag()].orEmpty()
    if (ftl.isNotEmpty()) {
        val resource = FluentResource.tryNew(ftl).getOrNull()
        if (resource != null) bundle.addResource(resource)
    }
    bundle.addBuiltins()
    return bundle
}

private fun runQuery(bundle: FluentBundle, query: Query): QueryResult {
    val args = query.args?.let { map ->
        val a = FluentArgs()
        map.forEach { (k, v) -> a.set(k, v) }
        a
    }
    val result = bundle.format(query.id, args)
    return QueryResult(query.id, query.expected, result, result == query.expected)
}

data class ScenarioResult(
    val name: String,
    val passed: Boolean,
    val results: List<QueryResult>,
    val errors: List<String>,
)
data class QueryResult(val id: String, val expected: String?, val actual: String?, val passed: Boolean)
