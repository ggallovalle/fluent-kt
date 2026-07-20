# Changelog

All notable changes to fluent-kt are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `fluent-codegen` + `fluent-gradle-plugin` (`dev.kbroom.fluent`): multi-bundle
  FTL layout (`{locale}/{bundle}/**/*.ftl`), `fluentValidate` /
  `fluentGenerate` / `fluentScaffoldLocale`, typed `*Messages` /
  `*L10n` wrappers with KDoc, `FtlIds`, and `*Resources` (`ResourceId`)
  for fluent-fallback. Not published yet — apply from this repo /
  included build.
- `benchmarks` module with `kotlinx-benchmark` (JMH on JVM, linuxX64 target
  registered): parse / `tryNew` / serialize / `formatMessage` /
  `formatPattern` hot-path microbenchmarks. Run
  `./gradlew :benchmarks:jvmSmokeBenchmark` or `:benchmarks:jvmBenchmark`.
- Dokka HTML API docs (`./gradlew dokkaGenerate`) with GitHub Pages deploy
  workflow (`.github/workflows/docs.yml` → `https://ggallovalle.github.io/fluent-kt/`).
- Immutable-by-construction `FluentBundle` with a fluent DSL (`fluentBundle(locales) { ... }`)
  and a separate `FluentBundleBuilder` for imperative construction.
- Public API additions on `FluentBundle`: `entries()`, `getEntry(id)`, `hasTerm(id)`,
  `hasFunction(id)`, `formatAttribute(id, attribute, args)`,
  `formatMessageWithErrors(id, args)`.
- Public API additions on `FluentMessage`: `attributesMap(): Map<String, Pattern>`.
- Built-in functions: `DATETIME`, `DATE`, `TIME`, `LIST` (registered by `addBuiltins()`).
- `NUMBER` accepts named options (`style`, `currency`, `currencyDisplay`,
  `minimumFractionDigits`, `maximumFractionDigits`, `useGrouping`) matching
  fluent-rs's positional+named calling convention.
- `PLURAL` accepts `ordinal: "true"` as a named argument (ordinal plural
  categories; English `one`/`two`/`few`/`other` rules).
- Pseudolocalization modes: `Accented`, `Bidi` (RTL-embedding via RLE/PDF),
  `Long` (length padding via filler characters), `Widened`, `Hidden`.
  `createPseudoTransform(mode)` factory for bundle integration.
- `Localization` improvements: `formatAttribute` uses AST path via
  `FluentMessage.getAttributeValue`; `getAvailableLocales` uses
  `entries().isEmpty()` (replaces stale `hasMessage("")` sentinel);
  `formatWithErrors` surfaces resolver errors via `Pair<String?, List<FluentError>>`.
- Thread-safe memoization: `IntlLangMemoizer` uses copy-on-write `AtomicRef`
  via `kotlinx-atomicfu` (replaces non-portable `synchronized` blocks).

### Changed
- `FluentBundle` is now immutable by construction. Mutators moved to
  `FluentBundleBuilder` (notably `addResource`, `addResourceOverriding`,
  `addFunction`, `addBuiltins`, `setTransform`, `setUseIsolating`,
  `setFormatter`). The previous runtime `seal()`/`isSealed()` mechanism
  is gone — the type system enforces immutability.
- `JvmConcurrentFluentBundle` removed. The new `FluentBundle` is safe
  for concurrent reads by construction; the read-write lock was
  incomplete (didn't cover `IntlLangMemoizer`).
- `setUseIsolating` on the builder is real (resolves per call) instead of
  a no-op stub.
- NUMBER positional-options parsing replaced by named-options parsing.
- Native `PlatformIntl.formatTime` produces real `HH:MM:SS[AM/PM]` output
  instead of falling back to a date stub.

### Removed
- `JvmConcurrentFluentBundle` (jvmMain).
- `fluent-compiler/`, `fluent-compiler-annotations/`, `fluent-sample/` — empty
  shells never implemented. (`fluent-gradle-plugin` was deleted then recreated
  as AST-driven codegen; see Added.)
- Unused `kotlinx-datetime` dependency across all modules.

### Fixed
- `Localization.formatWithErrors` previously returned an empty error list
  on missing references; now returns the actual `FluentError`s collected
  during resolution.

### Security
- Resolver caps total placeable references at 100 per `formatPattern` call
  (`MAX_PLACEABLES`); parser caps nested placeable depth at 100
  (`MAX_PLACEABLE_DEPTH`). Defends against billion-laughs exponential
  fan-out and deep nesting stack overflow.
- Cycle detection for term references via shared `placeables` set across
  child scopes.
