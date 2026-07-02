package dev.kbroom.fluent.testing.bundle

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root fixture structure matching fluent-rs YAML format.
 */
@Serializable
data class TestFixture(
    val suites: List<TestSuite>
)

/**
 * Default configuration loaded from defaults.yaml.
 */
@Serializable
data class TestDefaults(
    val bundle: BundleDefaults
)

@Serializable
data class BundleDefaults(
    val locales: List<String>? = null,
    @SerialName("useIsolating")
    val useIsolating: Boolean? = null,
    val transform: String? = null
)

/**
 * A test suite - can contain nested suites, resources, bundles, and tests.
 */
@Serializable
data class TestSuite(
    val name: String,
    val skip: Boolean? = null,
    val resources: List<TestResource>? = null,
    val bundles: List<TestBundle>? = null,
    val tests: List<TestCase>? = null,
    val suites: List<TestSuite>? = null
)

/**
 * Bundle configuration.
 */
@Serializable
data class TestBundle(
    val name: String? = null,
    val locales: List<String>? = null,
    @SerialName("useIsolating")
    val useIsolating: Boolean? = null,
    val transform: String? = null,
    val functions: List<String>? = null,
    val resources: List<String>? = null,
    val errors: List<TestError>? = null
)

/**
 * A resource (FTL source string).
 */
@Serializable
data class TestResource(
    val source: String,
    val name: String? = null,
    val errors: List<TestError>? = null
)

/**
 * A single test with assertions.
 */
@Serializable
data class TestCase(
    val name: String,
    val skip: Boolean? = null,
    val resources: List<TestResource>? = null,
    val bundles: List<TestBundle>? = null,
    val asserts: List<TestAssert>
)

/**
 * An assertion about a formatted message.
 */
@Serializable
data class TestAssert(
    val id: String,
    val value: String? = null,
    val attribute: String? = null,
    val bundle: String? = null,
    val missing: Boolean? = null,
    val args: Map<String, TestArgValue>? = null,
    val errors: List<TestError>? = null
)

/**
 * Argument value - simplified as string or number map entry.
 */
@Serializable
data class TestArgValue(
    val value: String? = null,
    val numValue: Double? = null
) {
    companion object {
        fun fromString(v: String) = TestArgValue(value = v)
        fun fromNumber(v: Double) = TestArgValue(numValue = v)
    }
}

/**
 * Expected error in test assertion.
 */
@Serializable
data class TestError(
    @SerialName("type")
    val errorType: String,
    val desc: String? = null
)
