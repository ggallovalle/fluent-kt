package dev.kbroom.fluent.benchmark

import dev.kbroom.fluent.bundle.FluentArgs
import dev.kbroom.fluent.bundle.FluentBundle
import dev.kbroom.fluent.bundle.FluentResource
import dev.kbroom.fluent.bundle.builtins
import dev.kbroom.fluent.bundle.fluentArgsOf
import dev.kbroom.fluent.bundle.fluentBundle
import dev.kbroom.fluent.bundle.resource
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.syntax.Pattern
import dev.kbroom.fluent.syntax.Resource
import dev.kbroom.fluent.syntax.parser.FluentParser
import dev.kbroom.fluent.syntax.serializer.Serializer
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup

/**
 * Microbenchmarks for the hot paths listed in todo/08-performance.md §A.
 *
 * Run:
 * ```
 * ./gradlew :benchmarks:jvmBenchmark          # full profile
 * ./gradlew :benchmarks:jvmSmokeBenchmark     # quick smoke
 * ./gradlew :benchmarks:linuxX64Benchmark     # Native (when available)
 * ```
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
class FluentHotPathBenchmark {
    private lateinit var ftlSource: String
    private lateinit var parsedResource: Resource
    private lateinit var bundle: FluentBundle
    private lateinit var placeablePattern: Pattern
    private lateinit var formatArgs: FluentArgs
    private val parser = FluentParser()
    private val serializer = Serializer()
    private val locales = listOf(LanguageIdentifier.parse("en-US"))

    @Setup
    fun setUp() {
        ftlSource = BenchmarkFixtures.hundredMessageFtl
        parsedResource = parser.parse(ftlSource)
        bundle = fluentBundle(locales) {
            resource(BenchmarkFixtures.placeableFtl)
            builtins()
        }
        placeablePattern = requireNotNull(bundle.getMessage("hello")?.value()) {
            "hello message missing from placeable fixture"
        }
        formatArgs = fluentArgsOf("name" to "World", "count" to 42)
    }

    /** Parse a ~100-message FTL file into an AST [Resource]. */
    @Benchmark
    fun parseHundredMessages(bh: Blackhole) {
        bh.consume(parser.parse(ftlSource))
    }

    /** Parse + wrap into a [FluentResource] (public entry for loading FTL). */
    @Benchmark
    fun tryNewResource(bh: Blackhole) {
        bh.consume(FluentResource.tryNew(ftlSource).getOrThrow())
    }

    /** Serialize a previously-parsed ~100-message AST back to FTL text. */
    @Benchmark
    fun serializeHundredMessages(bh: Blackhole) {
        bh.consume(serializer.serialize(parsedResource))
    }

    /** Format a message that contains a variable placeable. */
    @Benchmark
    fun formatMessageWithArgs(bh: Blackhole) {
        bh.consume(bundle.formatMessage("hello", formatArgs))
    }

    /** Format a [Pattern] directly (skips message-id lookup). */
    @Benchmark
    fun formatPatternWithPlaceables(bh: Blackhole) {
        bh.consume(bundle.formatPattern(placeablePattern, formatArgs))
    }
}
