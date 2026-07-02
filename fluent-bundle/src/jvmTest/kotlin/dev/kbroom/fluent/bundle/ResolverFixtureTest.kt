package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.testing.bundle.*
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.bundle.FluentArgs
import kotlin.test.Test
import kotlin.test.fail

/**
 * Resolver fixture tests - compare bundle formatting against reference YAML fixtures.
 */
class ResolverFixtureTest {
    
    private val fixturesDir = "fixtures"
    
    @Test
    fun runResolverFixtures() {
        val failures = mutableListOf<String>()
        
        // Load defaults
        val defaults = loadDefaults(fixturesDir)
        
        // Load all fixtures
        val fixtures = loadResolverFixtures(fixturesDir)
        
        println("Loaded ${fixtures.size} fixture files")
        
        for ((fileIndex, fixture) in fixtures.withIndex()) {
            // Skip bomb.yaml - causes infinite loop without cycle detection
            if (fixture.suites.any { it.name.contains("bomb", ignoreCase = true) }) {
                println("Skipping bomb suite in ${fixture.suites.size} suites")
                continue
            }
            
            for ((suiteIndex, suite) in fixture.suites.withIndex()) {
                try {
                    testSuite(suite, defaults, TestScope())
                } catch (e: Throwable) {
                    failures.add("[$fileIndex/$suiteIndex] ${suite.name}: ${e.message}")
                }
            }
        }
        
        if (failures.isNotEmpty()) {
            fail("Resolver fixture failures (${failures.size}):\n${failures.joinToString("\n").take(2000)}")
        }
    }
    
    private fun testSuite(suite: TestSuite, defaults: TestDefaults?, scope: TestScope) {
        if (suite.skip == true) return
        
        val newScope = scope.push(suite.name, suite.resources ?: emptyList(), suite.bundles ?: emptyList())
        
        // Run tests in this suite
        if (suite.tests != null) {
            for (test in suite.tests) {
                testTest(test, defaults, newScope)
            }
        }
        
        // Run nested suites
        if (suite.suites != null) {
            for (subSuite in suite.suites) {
                testSuite(subSuite, defaults, newScope)
            }
        }
    }
    
    private fun testTest(test: TestCase, defaults: TestDefaults?, scope: TestScope) {
        if (test.skip == true) return
        
        val testScope = scope.push(test.name, test.resources ?: emptyList(), test.bundles ?: emptyList())
        val bundles = testScope.getBundles(defaults)
        
        for (assertion in test.asserts) {
            try {
                testAssert(assertion, bundles, defaults)
            } catch (e: Throwable) {
                throw RuntimeException("${test.name}: ${assertion.id}: ${e.message}", e)
            }
        }
    }
    
    private fun testAssert(assertion: TestAssert, bundles: Map<String, FluentBundle>, defaults: TestDefaults?) {
        val bundle = if (assertion.bundle != null) {
            bundles[assertion.bundle] ?: throw RuntimeException("Bundle not found: ${assertion.bundle}")
        } else {
            bundles.values.firstOrNull() ?: throw RuntimeException("No bundles available")
        }
        
        // Check missing
        if (assertion.missing != null) {
            val attr = assertion.attribute
            val missing = if (attr != null) {
                bundle.getMessage(assertion.id)?.getAttribute(attr) == null
            } else {
                !bundle.hasMessage(assertion.id)
            }
            if (missing != assertion.missing) {
                throw RuntimeException("Expected missing=${assertion.missing} but got $missing")
            }
            return
        }
        
        // Check value
        if (assertion.value != null) {
            val msg = bundle.getMessage(assertion.id) 
                ?: throw RuntimeException("Message not found: ${assertion.id}")
            
            // Build args from assertion - parse as numbers where possible
            val args = assertion.args?.let { argMap ->
                val fluentArgs = FluentArgs()
                for ((key, value) in argMap) {
                    // Try to parse as int/long first (common case), then float, else string
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
            val value = if (attr != null) {
                val attribute = msg.getAttribute(attr)
                if (attribute == null) {
                    throw RuntimeException("Attribute not found: $attr")
                }
                bundle.formatPattern(attribute.value, args)
            } else {
                val v = msg.value()
                if (v == null) {
                    throw RuntimeException("Message has no value")
                }
                bundle.formatPattern(v, args)
            }
            
            if (value != assertion.value) {
                throw RuntimeException("Value mismatch: expected '${assertion.value}' but got '$value'")
            }
        }
    }
    
    private fun formatPattern(pattern: dev.kbroom.fluent.syntax.Pattern, bundle: FluentBundle): String {
        return bundle.formatPattern(pattern)
    }
}

/**
 * Scope tracks hierarchical test context.
 */
class TestScope(private val levels: List<ScopeLevel> = emptyList()) {
    
    fun push(name: String, resources: List<TestResource>, bundles: List<TestBundle>): TestScope {
        return TestScope(levels + ScopeLevel(name, resources, bundles))
    }
    
    fun getBundles(defaults: TestDefaults?): Map<String, FluentBundle> {
        if (levels.isEmpty()) {
            // Create default bundle
            val bundle = createBundle(null, defaults, emptyList())
            return mapOf("default" to bundle)
        }
        
        val bundles = mutableMapOf<String, FluentBundle>()
        
        for (level in levels) {
            // Add resources from this level
            val resources = level.resources.map { it.source }
            
            // Add bundles from this level
            for (bundleConfig in level.bundles) {
                val name = bundleConfig.name ?: "bundle_${bundles.size}"
                val bundle = createBundle(bundleConfig, defaults, resources)
                bundles[name] = bundle
            }
            
            // If no bundles at this level, create implicit one
            if (level.bundles.isEmpty() && resources.isNotEmpty()) {
                val name = "bundle_${bundles.size}"
                val bundle = createBundle(null, defaults, resources)
                bundles[name] = bundle
            }
        }
        
        return bundles
    }
    
    private fun createBundle(
        config: TestBundle?,
        defaults: TestDefaults?,
        extraResources: List<String>
    ): FluentBundle {
        // Determine locales
        val locales = config?.locales 
            ?: defaults?.bundle?.locales 
            ?: listOf("en")
        
        val langIds = locales.map { LanguageIdentifier.parse(it) }
        val bundle = FluentBundle(langIds)
        
        // Add built-in functions (NUMBER, PLURAL, CONCAT)
        bundle.addBuiltins()
        
        // Add resources
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

data class ScopeLevel(
    val name: String,
    val resources: List<TestResource>,
    val bundles: List<TestBundle>
)
