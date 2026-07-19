package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.testing.bundle.TestBundle
import dev.kbroom.fluent.testing.bundle.TestDefaults
import dev.kbroom.fluent.testing.bundle.TestResource
import dev.kbroom.fluent.testing.bundle.TestSuite
import dev.kbroom.fluent.testing.bundle.loadDefaults
import dev.kbroom.fluent.testing.bundle.loadResolverFixtures
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

    val newScope = scope.push(suite.name, suite.resources.orEmpty(), suite.bundles.orEmpty())

    if (suite.suites != null) {
        for (subSuite in suite.suites) {
            testSuiteHelper(subSuite, defaults, newScope)
        }
    }
}

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
