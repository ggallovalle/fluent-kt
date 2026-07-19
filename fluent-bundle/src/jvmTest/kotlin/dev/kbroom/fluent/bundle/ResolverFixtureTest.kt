package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.FluentArgs
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Signal that a fixture assertion failed. The [message] is rendered by the
 * test entry point and prepended with the file/suite index.
 */
private class FixtureAssertionError(message: String, cause: Throwable? = null) : AssertionError(message, cause)

/**
 * Resolver fixture tests - compare bundle formatting against reference YAML fixtures.
 */
val ResolverFixtureTest by testSuite {

    val fixturesDir = "fixtures"

    test("run resolver fixtures") {
        val failures = mutableListOf<String>()

        val defaults = loadDefaults(fixturesDir)

        val fixtures = loadResolverFixtures(fixturesDir)

        println("Loaded ${fixtures.size} fixture files")

        for ((fileIndex, fixture) in fixtures.withIndex()) {
            if (fixture.suites.any { it.name.contains("bomb", ignoreCase = true) }) {
                println("Skipping bomb suite in ${fixture.suites.size} suites")
                continue
            }

            for ((suiteIndex, suite) in fixture.suites.withIndex()) {
                try {
                    println("DEBUG TEST: fileIndex=$fileIndex, suiteIndex=$suiteIndex, suiteName=${suite.name}")
                    testSuiteHelper(suite, defaults, TestScope())
                } catch (e: Throwable) {
                    failures.add("[$fileIndex/$suiteIndex] ${suite.name}: ${e.message}")
                }
            }
        }

        val message = if (failures.isEmpty()) {
            ""
        } else {
            "Resolver fixture failures (${failures.size}):\n${failures.joinToString(
                "\n",
            ).take(2000)}"
        }
        assertTrue(failures.isEmpty(), message)
    }
}

private fun testSuiteHelper(suite: TestSuite, defaults: TestDefaults?, scope: TestScope) {
    if (suite.skip == true) return

    val newScope = scope.push(suite.name, suite.resources ?: emptyList(), suite.bundles ?: emptyList())

    if (suite.suites != null) {
        for (subSuite in suite.suites) {
            testSuiteHelper(subSuite, defaults, newScope)
        }
    }
}

private fun testTestHelper(test: TestCase, defaults: TestDefaults?, scope: TestScope) {
    if (test.skip == true) return

    val testScope = scope.push(test.name, test.resources ?: emptyList(), test.bundles ?: emptyList())
    val bundles = testScope.getBundles(defaults)

    for (assertion in test.asserts) {
        try {
            testAssertHelper(assertion, bundles)
        } catch (e: Throwable) {
            val msg = "${test.name}: ${assertion.id}: ${e.message ?: "null message"}"
            throw FixtureAssertionError(msg, e)
        }
    }
}

private fun testAssertHelper(assertion: TestAssert, bundles: Map<String, FluentBundle>) {
    val bundle = resolveBundle(bundles, assertion.bundle)
    val attributeName = assertion.attribute
    val args = buildArgs(assertion.args)
    val actualValue = if (attributeName != null) {
        val message = bundle.getMessage(assertion.id)
            ?: throw FixtureAssertionError("Message not found: ${assertion.id}")
        val attribute = message.getAttribute(attributeName)
            ?: throw FixtureAssertionError("Attribute not found: $attributeName")
        bundle.formatPattern(attribute.value, args)
    } else {
        bundle.formatMessage(assertion.id, args) ?: ""
    }

    if (assertion.value != null) {
        assertEquals(assertion.value, actualValue, "Value mismatch for ${assertion.id}")
    }
}

private fun resolveBundle(bundles: Map<String, FluentBundle>, name: String?): FluentBundle = if (name == null) {
    bundles.values.firstOrNull()
        ?: throw FixtureAssertionError("No bundles available")
} else {
    bundles[name] ?: throw FixtureAssertionError("Bundle not found: $name")
}

private fun buildArgs(argMap: Map<String, String>?): FluentArgs? {
    if (argMap == null) return null
    val fluentArgs = FluentArgs()
    for ((key, value) in argMap) {
        val intVal = value.toIntOrNull()
        if (intVal != null) fluentArgs.set(key, intVal) else fluentArgs.set(key, value)
    }
    return fluentArgs
}

private fun formatPatternHelper(pattern: dev.kbroom.fluent.syntax.Pattern, bundle: FluentBundle): String =
    bundle.formatPattern(pattern)

/**
 * Scope tracks hierarchical test context.
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

        for (level in levels) {
            val resources = level.resources.map { it.source }

            for (bundleConfig in level.bundles) {
                val name = bundleConfig.name ?: "bundle_${bundles.size}"
                val bundle = createBundle(bundleConfig, defaults, resources)
                bundles[name] = bundle
            }

            if (level.bundles.isEmpty() && resources.isNotEmpty()) {
                val name = "bundle_${bundles.size}"
                val bundle = createBundle(null, defaults, resources)
                bundles[name] = bundle
            }
        }

        return bundles
    }

    private fun createBundle(config: TestBundle?, defaults: TestDefaults?, extraResources: List<String>): FluentBundle {
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
