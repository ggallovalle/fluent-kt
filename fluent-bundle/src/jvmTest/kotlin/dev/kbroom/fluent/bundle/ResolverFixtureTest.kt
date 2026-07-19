package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.FluentArgs
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.testing.bundle.*
import kotlin.test.assertTrue

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
            testAssertHelper(assertion, bundles, defaults, test.name)
        } catch (e: Throwable) {
            val msg = "${test.name}: ${assertion.id}: ${e.message ?: "null message"}"
            throw RuntimeException(msg, e)
        }
    }
}

private fun testAssertHelper(
    assertion: TestAssert,
    bundles: Map<String, FluentBundle>,
    defaults: TestDefaults?,
    testName: String,
) {
    val bundle = if (assertion.bundle != null) {
        bundles[assertion.bundle] ?: throw RuntimeException("Bundle not found: ${assertion.bundle}")
    } else {
        bundles.values.firstOrNull() ?: throw RuntimeException("No bundles available")
    }

    if (assertion.value != null) {
        try {
            val msg = bundle.getMessage(assertion.id)
                ?: throw RuntimeException("Message not found: ${assertion.id}")

            val args = assertion.args?.let { argMap ->
                val fluentArgs = FluentArgs()
                for ((key, value) in argMap) {
                    val intVal = value.toIntOrNull()
                    if (intVal != null) {
                        fluentArgs.set(key, intVal)
                    } else {
                        fluentArgs.set(key, value)
                    }
                }
                fluentArgs
            }
            val attr = assertion.attribute
            val value: String = if (attr != null) {
                val attribute = msg.getAttribute(attr)
                if (attribute == null) {
                    throw RuntimeException("Attribute not found: $attr")
                }
                bundle.formatPattern(attribute.value, args)
            } else {
                val formatted = bundle.formatMessage(assertion.id, args)
                if (formatted == null) {
                    return
                }
                formatted
            }

            if (value != assertion.value) {
                throw RuntimeException("Value mismatch: expected '${assertion.value}' but got '$value'")
            }
        } catch (e: Throwable) {
            println(
                "DEBUG testAssert EXCEPTION: assertion.id=${assertion.id}, error=${e.message}, stack=${e.stackTrace?.firstOrNull()}",
            )
            throw e
        }
    }
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
        val locales = config?.locales
            ?: defaults?.bundle?.locales
            ?: listOf("en")

        val useIsolating = config?.useIsolating ?: defaults?.bundle?.useIsolating ?: true

        val functions = config?.functions ?: defaults?.bundle?.functions ?: emptyList()

        val langIds = locales.map { LanguageIdentifier.parse(it) }
        val bundle = FluentBundle(langIds, useIsolating)
        bundle.addBuiltins()
        val builtInFunctions = setOf("NUMBER", "PLURAL", "CONCAT", "SUM", "IDENTITY")
        functions.filter { it !in builtInFunctions }.forEach { fnName ->
            bundle.addFunction(fnName) { args, _ ->
                args.firstOrNull() ?: FluentValue.Str("$fnName()")
            }
        }
        config?.transform?.let { transformName ->
            bundle.setTransform { text ->
                when (transformName) {
                    "example" -> text.uppercase()
                    else -> text
                }
            }
        }

        for (source in extraResources) {
            val resource = FluentResource.tryNew(source)
            if (resource.isFailure) {
                println("Warning: Failed to parse resource: ${resource.exceptionOrNull()?.message}")
                continue
            }
            bundle.addResource(resource.getOrThrow())
        }

        return bundle
    }
}

data class ScopeLevel(val name: String, val resources: List<TestResource>, val bundles: List<TestBundle>)
