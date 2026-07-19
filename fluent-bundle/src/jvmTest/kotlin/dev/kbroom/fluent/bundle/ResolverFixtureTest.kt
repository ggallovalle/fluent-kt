package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.resolver.ResolverError
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.testing.bundle.TestAssert
import dev.kbroom.fluent.testing.bundle.TestBundle
import dev.kbroom.fluent.testing.bundle.TestCase
import dev.kbroom.fluent.testing.bundle.TestDefaults
import dev.kbroom.fluent.testing.bundle.TestResource
import dev.kbroom.fluent.testing.bundle.TestSuite
import dev.kbroom.fluent.testing.bundle.loadDefaults
import dev.kbroom.fluent.testing.bundle.loadResolverFixtures
import kotlin.test.assertTrue

/**
 * ResolverFixtureTest - actually runs each fixture's tests and asserts the
 * bundle's format output matches the expected value.  The previous version
 * only walked the suite tree and never executed TestCases, so a fixture
 * with thousands of assertions would pass with zero assertions checked.
 */
val ResolverFixtureTest by testSuite {

    val fixturesDir = "fixtures"

    test("run resolver fixtures") {
        val failures = mutableListOf<String>()

        val defaults = loadDefaults(fixturesDir)
        val fixtures = loadResolverFixtures(fixturesDir)

        for ((fileIndex, fixture) in fixtures.withIndex()) {
            if (fixture.suites.any { it.name.contains("bomb", ignoreCase = true) }) continue

            for ((suiteIndex, suite) in fixture.suites.withIndex()) {
                runSuite(suite, defaults, TestScope(), "file=$fileIndex suite=$suiteIndex", failures)
            }
        }

        val message = if (failures.isEmpty()) {
            ""
        } else {
            "Resolver fixture failures (${failures.size}):\n${failures.joinToString("\n").take(2000)}"
        }
        assertTrue(failures.isEmpty(), message)
    }
}

/**
 * Recursively run a test suite, executing TestCases and reporting failures.
 * Errors are accumulated rather than thrown so a single test report can
 * surface every regression in one run.
 */
private fun runSuite(
    suite: TestSuite,
    defaults: TestDefaults?,
    scope: TestScope,
    path: String,
    failures: MutableList<String>,
) {
    if (suite.skip == true) return

    val newScope = scope.push(suite.name, suite.resources.orEmpty(), suite.bundles.orEmpty())

    suite.tests?.forEach { testCase ->
        if (testCase.skip != true) {
            runTestCase(testCase, defaults, newScope, "$path/${suite.name}", failures)
        }
    }

    suite.suites?.forEach { subSuite ->
        runSuite(subSuite, defaults, newScope, "$path/${suite.name}", failures)
    }
}

/**
 * Execute one TestCase by resolving its bundles, running each assert, and
 * reporting any value, attribute, missing, args, or error mismatches.
 */
private fun runTestCase(
    testCase: TestCase,
    defaults: TestDefaults?,
    scope: TestScope,
    path: String,
    failures: MutableList<String>,
) {
    val testScope = if (testCase.resources.isNullOrEmpty() && testCase.bundles.isNullOrEmpty()) {
        scope
    } else {
        scope.push(testCase.name, testCase.resources.orEmpty(), testCase.bundles.orEmpty())
    }

    val bundles = testScope.getBundles(defaults)
    val casePath = "$path/${testCase.name}"

    testCase.asserts.forEachIndexed { i, assert ->
        runAssert(assert, bundles, casePath, i, failures)
    }
}

private fun runAssert(
    assert: TestAssert,
    bundles: Map<String, FluentBundle>,
    casePath: String,
    index: Int,
    failures: MutableList<String>,
) {
    val bundleName = assert.bundle ?: bundles.keys.firstOrNull() ?: return
    val bundle = bundles[bundleName]
        ?: run {
            failures.add("$casePath assert[$index] id=${assert.id}: missing bundle '$bundleName'")
            return
        }

    val args = buildArgs(assert.args)
    val errors = mutableListOf<FluentError>()
    val message = bundle.getMessage(assert.id)
    val attributeName = assert.attribute
    val actual: String? = if (attributeName != null) {
        val attrValue = message?.getAttributeValue(attributeName)
        if (attrValue != null) {
            bundle.formatPattern(attrValue, args, errors)
        } else {
            null
        }
    } else {
        val pattern = message?.value()
        if (pattern != null) {
            bundle.formatPattern(pattern, args, errors)
        } else {
            null
        }
    }

    // value expectation
    if (assert.value != null) {
        if (actual != assert.value) {
            failures.add(
                "$casePath assert[$index] id=${assert.id}: expected <${assert.value}> got <$actual>",
            )
        }
    } else if (assert.missing == true) {
        if (actual != null) {
            failures.add("$casePath assert[$index] id=${assert.id}: expected missing got <$actual>")
        }
    }

    // error expectation
    val actualErrorKinds = errors.mapNotNull { it.toExpectedKind() }.toSet()
    val expectedErrorKinds = assert.errors?.map { it.errorType }.orEmpty().toSet()
    if (actualErrorKinds != expectedErrorKinds) {
        failures.add(
            "$casePath assert[$index] id=${assert.id}: errors mismatch " +
                "expected=$expectedErrorKinds actual=$actualErrorKinds",
        )
    }
}

private fun buildArgs(raw: Map<String, String>?): FluentArgs? {
    if (raw.isNullOrEmpty()) return null
    val args = FluentArgs()
    for ((k, v) in raw) {
        // The fixture loader passes strings as-is (e.g. "3"). Pass through as
        // a string and let the FluentValue factory coerce to a number when
        // it looks like one.
        args.set(k, v)
    }
    return args
}

/**
 * Map a runtime FluentError to the kind strings the fixtures use
 * (Reference, Cyclic, NoValue, Parser, etc.).
 */
private fun FluentError.toExpectedKind(): String? = when (this) {
    is FluentError.ResolverError -> when (error) {
        is ResolverError.Reference -> "Reference"
        is ResolverError.Cyclic -> "Cyclic"
        is ResolverError.NoValue -> "NoValue"
        is ResolverError.MissingDefault -> "MissingDefault"
        else -> null
    }

    is FluentError.Overriding -> "Overriding"

    is FluentError.ParserError -> "Parser"

    else -> null
}

/**
 * Scope tracks hierarchical test context.
 *
 * Each level contributes its resources and bundle configurations; when a
 * level defines bundles, they are added under their configured names; if
 * a level defines only resources (no bundles), an implicit "bundle_N"
 * is created for those resources.
 */
class TestScope(private val levels: List<ScopeLevel> = emptyList()) {

    fun push(name: String, resources: List<TestResource>, bundles: List<TestBundle>): TestScope =
        TestScope(levels + ScopeLevel(name, resources, bundles))

    fun getBundles(defaults: TestDefaults?): Map<String, FluentBundle> {
        if (levels.isEmpty()) {
            val bundle = createBundle(null, defaults, emptyList())
            return mapOf("default" to bundle)
        }

        val bundles = mutableMapOf<String, FluentBundle>()
        val allResources = levels.flatMap { it.resources }.map { it.source }

        for (level in levels) {
            if (level.bundles.isNotEmpty()) {
                for (bundleConfig in level.bundles) {
                    val name = bundleConfig.name ?: "bundle_${bundles.size}"
                    val bundle = createBundle(bundleConfig, defaults, level.resources.map { it.source })
                    bundles[name] = bundle
                }
            } else if (level.resources.isNotEmpty()) {
                val name = "bundle_${bundles.size}"
                val bundle = createBundle(null, defaults, level.resources.map { it.source })
                bundles[name] = bundle
            }
        }

        // If we got no bundles from any level (e.g. all levels had only suite
        // names without resources/bundles), fall back to a single default.
        if (bundles.isEmpty() && allResources.isNotEmpty()) {
            bundles["default"] = createBundle(null, defaults, allResources)
        }

        return bundles
    }

    private fun createBundle(
        config: TestBundle?,
        defaults: TestDefaults?,
        extraResources: List<String>,
    ): FluentBundle {
        val locales = firstNonNull(config?.locales, defaults?.bundle?.locales, listOf("en"))
        val useIsolating = firstNonNull(config?.useIsolating, defaults?.bundle?.useIsolating, true)
        val functions = firstNonNull(config?.functions, defaults?.bundle?.functions, emptyList())
        val langIds = locales.map { LanguageIdentifier.parse(it) }
        val bundle = FluentBundle(langIds, useIsolating)
        bundle.addBuiltins()
        registerFixtureFunctions(bundle, functions)
        applyFixtureTransform(bundle, config?.transform)
        registerExtraResources(bundle, extraResources)
        return bundle
    }

    private fun <T> firstNonNull(a: T?, b: T?, c: T): T = a ?: b ?: c

    private fun registerFixtureFunctions(bundle: FluentBundle, functions: List<String>) {
        val builtInFunctions = setOf("NUMBER", "PLURAL", "CONCAT", "SUM", "IDENTITY")
        functions.filter { it !in builtInFunctions }.forEach { fnName ->
            bundle.addFunction(fnName) { args, _ ->
                args.firstOrNull() ?: FluentValue.Str("$fnName()")
            }
        }
    }

    private fun applyFixtureTransform(bundle: FluentBundle, transformName: String?) {
        transformName ?: return
        bundle.setTransform { text ->
            when (transformName) {
                "example" -> text.uppercase()
                else -> text
            }
        }
    }

    private fun registerExtraResources(bundle: FluentBundle, extraResources: List<String>) {
        for (source in extraResources) {
            val resource = FluentResource.tryNew(source)
            if (resource.isFailure) {
                println("Warning: Failed to parse resource: ${resource.exceptionOrNull()?.message}")
                continue
            }
            bundle.addResource(resource.getOrThrow())
        }
    }
}

data class ScopeLevel(val name: String, val resources: List<TestResource>, val bundles: List<TestBundle>)
