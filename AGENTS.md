# Agent Guidelines

You are working on **fluent-kt**, a Kotlin Multiplatform port of [Mozilla's Fluent](https://projectfluent.org/) localization system. This file is the source of truth for project conventions and architecture decisions that aren't obvious from reading code in isolation. The companion document for change workflow is [CONTRIBUTING.md](CONTRIBUTING.md); for release notes see [CHANGELOG.md](CHANGELOG.md).

## TL;DR

- **`fluentBundle(locales) { ... }` DSL is the public entry point.** Mutators live on `FluentBundleBuilder`. The bundle is immutable by construction — no `seal()`, no `setUseIsolating` on the bundle itself.
- **Bunles are thread-safe for concurrent reads by construction.** Internal state is set once at build time; the memoizer uses copy-on-write `AtomicRef` (no locks).
- **Tests come first, TDD-style.** Failing test commit, then implementation commit, then refactor. Use **testBalloon** (`testSuite { test("…") { … } }`), not JUnit `@Test` classes — exception: `fluent-compose` Robolectric tests use JUnit4 `@RunWith(RobolectricTestRunner)` because `ApplicationProvider` needs that runner. `./gradlew jvmTest linuxX64Test detektAll` must be green before any commit (plus `:fluent-compose:testDebugUnitTest` when touching Compose).
- **CommonMain is sacred.** No `java.util.concurrent`, no `java.time`, no `java.io.File` in `commonMain`. Use platform-specific source sets (`jvmMain`, `linuxX64Main`) for those, and `expect`/`actual` declarations when needed.

## Module map

| Module | Concern | Touch when |
|---|---|---|
| `fluent-syntax` | FTL parser, AST, serializer | Adding a syntax feature, changing parser error semantics. No runtime. |
| `intl-memoizer` | Locale-aware formatters (numbers, dates, lists) + thread-safe cache | Adding/changing `PlatformIntl.format*` impls. Cache code is here, not in `fluent-bundle`. |
| `fluent-bundle` | `FluentBundle` runtime, resolver, builder DSL, builtin functions | The main API surface. Most changes land here. |
| `fluent-pseudo` | Pseudolocalization transforms (`Accented`, `Bidi`, `Long`, `Widened`, `Hidden`) | Adding a new pseudo mode or `createPseudoTransform` factory. |
| `fluent-fallback` | `Localization` facade, locale chain fallback | Changing fallback semantics, error-collection contract. |
| `fluent-resmgr` | Filesystem resource loading | Adding new filesystem conventions (Android assets, JARs, etc.). |
| `fluent-testing` | Test helpers, shared fixtures, fluent-rs upstream data loader | Adding reusable test infrastructure. Tests elsewhere depend on this. |
| `fluent` | Public umbrella re-exports | New entry point that the umbrella should expose. |
| `fluent-codegen` | Layout discovery, AST→`BundleModel`, Kotlin emitter, locale scaffold | Changing generated API shape or validation rules. |
| `fluent-gradle-plugin` | Gradle plugin `dev.kbroom.fluent` (validate / generate / scaffold) | Plugin extension, tasks, source-set wiring. |
| `fluent-compose` | Android Jetpack Compose: `ProvideFluentFromAssets`, registry, `fluentString` escape hatch | CompositionLocals, asset loading, locale reload. |
| `examples/android-compose` | Sample app validating Activity + `remember*Messages()` call site | Wiring codegen + Compose UI. |
| `benchmarks` | JMH / kotlinx-benchmark microbenchmarks (not published) | Adding or changing hot-path measurements. Run `:benchmarks:jvmBenchmark`. |

If you're not sure which module a change belongs in, look at the existing tests in that module's `commonTest` — they encode the intended surface.

## Code conventions

### Imports

**No wildcard imports in `commonMain`/`jvmMain`/`linuxX64Main`.** Always explicit. Tests (`*Test.kt`) may use wildcards.

```kotlin
// Good
import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.Pattern
import dev.kbroom.fluent.bundle.FluentBundle

// Bad — wildcard imports hide dependencies
import dev.kbroom.fluent.syntax.*
import dev.kbroom.fluent.bundle.*
```

**Don't re-export with `typealias`.** Import the type directly in the consumer. Type aliases are for genuine semantic renames, not for routing imports around.

```kotlin
// Good
import dev.kbroom.fluent.bundle.FluentBundle
fun create(): FluentBundle = FluentBundle.builder(listOf()).build()

// Bad — typealias as a re-export
typealias Fluent = dev.kbroom.fluent.bundle.FluentBundle
```

### Builder + DSL pattern

Any configurable public object should be built through a builder with an immutable result, and exposed through a `xxx { ... }` DSL:

```kotlin
// The canonical way to construct a bundle:
val bundle = fluentBundle(locales = listOf(LanguageIdentifier.parse("en"))) {
    resource("hello = Hello, { $name }!")   // inline FTL
    resource(File("messages.ftl"))            // JVM only
    builtins()
    transform { it.uppercase() }
    function("HELLO") { args, _ -> FluentValue.Str("Hi") }
}

// The imperative fallback (no DSL):
val bundle = FluentBundleBuilder.builder(locales)
    .addResource(FluentResource.tryNew("hi = Hello").getOrThrow())
    .addBuiltins()
    .build()
```

When introducing a new configurable thing:
- The result type is immutable — its constructor is `internal`, takes the builder, and reads via accessor properties.
- All mutators live on the builder. They return `this` (the builder) for chaining.
- Expose DSL sugar via extension functions in the same file as the result type.

### Naming conventions

- `fluentXxx(locales) { ... }` — DSL factory functions (e.g. `fluentBundle`, `fluentArgs`, `parseFtl`).
- `FluentBundleBuilder.xxx(...)` — builder mutators, all return the builder.
- `FluentBundle.xxx(...)` — read-only methods on the immutable bundle.

If a method mutates state, it belongs on the builder. If it reads, it belongs on the bundle.

## KMP portability rules

These are enforced by code review, not by tooling:

- **`commonMain` may not reference `java.*`, `kotlinx-coroutines-android`, `java.io.File`, or any JVM-only API.** Use platform-specific source sets.
- **Thread-safety primitives must be KMP-portable.** Use `kotlinx.atomicfu` (works on JVM, Native, JS) rather than `synchronized {}` (JVM-only) or `ReentrantLock` (JVM-only).
- **Native tests use workers, not `Thread`.** Anything testing concurrency on Native must use `kotlinx.coroutines` workers or platform-appropriate primitives.
- **`expect`/`actual` declarations are fine** but keep the `expect` interface narrow — only what's actually platform-specific.

If you find yourself reaching for a JVM-only API in `commonMain`, the right move is:
1. Add an `expect` declaration in `commonMain`.
2. Add the `actual` implementation under `jvmMain` / `linuxX64Main` / etc.
3. For now, throw `UnsupportedOperationException` on platforms you can't easily support.

## Test conventions

### Framework: testBalloon only

**All tests use [testBalloon](https://github.com/infix-de/testBalloon)** — including
JVM-only modules (`fluent-codegen`, `fluent-gradle-plugin`). Do **not** use
JUnit `@Test` / `class FooTest`, Kotest specs, or `tasks.test { useJUnitPlatform() }`
as the primary harness.

Pattern (see any `*Test.kt` in `fluent-syntax` / `fluent-bundle`):

```kotlin
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

val FluentThingTest by testSuite {
    test("does the thing") {
        assertEquals(expected, actual)
    }
}
```

Module wiring:

```kotlin
plugins {
    id("de.infix.testBalloon") // or with version if root does not apply false
}
// dependencies:
testImplementation("de.infix.testBalloon:testBalloon-framework-core:1.0.1-K2.4.0")
// KMP: put that in commonTest / jvmTest source sets, not a raw tasks.test {} block
```

Assertions still come from `kotlin.test` (`assertEquals`, `assertTrue`, …).
Wildcards are allowed in test files only.

### TDD discipline

Every behavioral change follows this sequence:

1. **Red**: write a failing test in the appropriate `commonTest` (or `jvmTest` if it needs JVM-only APIs).
2. **Verify it fails for the right reason** — not a typo, not an unrelated compile error.
3. **Green**: minimum implementation to pass.
4. **Refactor**: clean up while keeping tests green.
5. **Commit the failing test separately** from the implementation commit so the history reads correctly.

### What tests to write

- **Resolver/parser** code: at least one happy-path test plus one adversarial test (cyclic reference, missing reference, billion-laughs fan-out, deeply nested placeable). See `MaliciousInputTest` and `ResolverFixtureTest` for the pattern.
- **Public API additions**: a test that calls the new API and asserts the contract.
- **Thread-safety claims**: a multi-thread test using whatever concurrency primitive the target supports. If you say "thread-safe", back it up with a test under `--rerun-tasks` to defeat any caching.
- **DSL sugar**: a test that constructs via the DSL and asserts the same shape as the imperative equivalent.

### Where tests live

- `commonTest/` — tests that run on every target (JVM + Native).
- `jvmTest/` — JVM-only tests (concurrent stress, JVM resource loading).
- `linuxX64Test/` — Native-only tests if any (none currently).

Most tests should be in `commonTest` — JVM-only test code rarely survives a Native port.

## Build and verify

Before any commit:

```bash
./gradlew jvmTest linuxX64Test detektAll
```

All three must be green. If you add a new dependency or change the version catalog, also run `./gradlew compileKotlinJvm compileKotlinLinuxX64` to catch KMP-portability mistakes early.

Detekt rules of note:
- `LongParameterList`: 6 function params, 8 constructor params (the `FluentBundle` internal constructor pulls from the builder, but be aware of the limit).
- `TooManyFunctions`: 25/class, 25/object — the builder legitimately has many methods.
- `ArgumentListWrapping` / `FunctionSignature` / `BlankLineBetweenWhenConditions`: disabled in `ktlint:` (the ktlint-wrapper plugin's namespace) — don't re-enable them in `style:`.

## What not to do

- **Don't add `seal()` / `isSealed()` / `@Volatile sealedFlag` patterns.** The bundle is immutable by construction. If you find yourself reaching for a runtime lock, the right move is `kotlinx.atomicfu` + an immutable data structure.
- **Don't hand-roll a `JVM-only` thread-safety wrapper.** The previous `JvmConcurrentFluentBundle` was deleted for exactly this reason — type system + atomicfu are sufficient.
- **Don't re-introduce `kotlinx-datetime` unless you actually use it.** It was removed because no source referenced it. Same goes for any "well I might need it later" additions to the version catalog.
- **Don't add a Kotlin target (macOS, iOS, JS, wasm) without being able to verify it.** See `todo/04-project-hygiene.md` for the rationale.
- **Don't write a 7-parameter constructor.** If you find yourself with that many fields, take a builder instead and have the constructor pull from it (see `FluentBundle` for the canonical pattern).
- **Don't add a `seal()` or similar runtime "freeze" flag** to a builder. The type system already enforces immutability.

## Where things live

- **Tests of public API contracts** → `*Test.kt` in `commonTest` of the relevant module.
- **Fixture-based tests** (replicating fluent-rs upstream tests) → use `fluent-testing`'s `loadResolverFixtures()` and put fixtures under `src/commonTest/resources/fixtures/`.
- **Detekt config** → `detekt.yml` at the repo root.
- **Version catalog** → `gradle/libs.versions.toml`.
- **Todo items** → `todo/*.md`. Move items through the boxes as you land them; update CHANGELOG.md at the same time.
