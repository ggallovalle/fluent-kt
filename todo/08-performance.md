# 08 — Performance

**Priority: LOW** — Benchmarks land in `:benchmarks`. Profile before
optimizing; typical apps are unlikely to be limited by Fluent.

## Current state

`kotlinx-benchmark` module (`:benchmarks`) covers the §A hot paths.
§B–E findings below are from code review against those paths; treat them
as hypotheses to confirm with JMH / allocation profiling before changing
runtime code.

## Tasks

### A. Microbenchmarks

- [x] **8.1** Add JMH benchmarks (JVM-only) for:
  - `FluentParser.parse()` — parse 100-message FTL file
  - `FluentBundle.formatMessage()` — format message with args
  - `FluentBundle.formatPattern()` — format pattern with placeables
  - `Serializer.serialize()` — serialize AST to string
  - `FluentResource.tryNew()` — parse + wrap

- [x] **8.2** Use `kotlinx-benchmark` for KMP-compatible benchmarks:
  - Plugin `org.jetbrains.kotlinx.benchmark` `0.4.17`
  - Benchmarks live in `benchmarks/src/commonMain`
  - Targets: `jvm` (JMH) + `linuxX64`
  - Profiles: `main` (default) and `smoke` (fast sanity)

  ```bash
  ./gradlew :benchmarks:jvmSmokeBenchmark
  ./gradlew :benchmarks:jvmBenchmark
  ./gradlew :benchmarks:linuxX64Benchmark
  ```

### B. Memory profiling

- [x] **8.3** Measure allocation patterns (code review; confirm with JMH
  `gc` profiler / async-profiler when optimizing):
  - `formatPattern` / `formatMessage` allocate a fresh `Scope` +
    `mutableListOf<FluentError>()` per call — pooling is unlikely to
    win until allocation profiles show Scope as a hotspot.
  - `PatternElement.TextElement` appends into a single `StringBuilder`
    in `PatternResolver.resolve` — already efficient.
  - `FluentValue.Str` is allocated when boxing resolved strings; expected
    for the value type. Prefer measuring before introducing caching.

- [x] **8.4** Kotlin/Native memory:
  - `FluentBundle` is immutable by construction (no `seal()`); entries /
    functions / transform are snapshots taken at `build()`.
  - Memoizer uses copy-on-write `AtomicRef` maps — safe to share across
    workers after handoff. Stale `seal()` KDoc on `IntlLangMemoizer`
    corrected.

### C. Caching

- [x] **8.5** `IntlLangMemoizer` already caches intl formatters. Verify:
  - **Hit rate**: first call per `(type|FormatterKey)` misses; subsequent
    calls hit. Reasonable for steady-state formatting.
  - **Growth**: caches are unbounded maps keyed by type / formatter
    options / locale. Growth is bounded by distinct keys the app creates,
    not by call count. No eviction — fine for typical locale+option sets;
    watch if callers invent unbounded option combinations.
  - **Thread-safety**: copy-on-write `AtomicRef` + CAS (see
    `intl-memoizer`); covered by existing concurrency tests.

- [ ] **8.6** Consider caching `formatMessage` results for constant
  messages (no args). **Defer** until JMH shows constant-message
  formatting as a hotspot — adds invalidation complexity for little
  gain if apps already hold the string.

### D. Bundle population

- [x] **8.7** `addResource` copies entries into a map. Findings:
  - Builder already uses `linkedMapOf()` (insertion-ordered
    `LinkedHashMap`) — no change needed for ordering.
  - `build()` snapshots via `entries.toMap()` (one copy). For 1000+
    messages this is a one-time cost at construction; not on the
    format hot path.
  - Revisit only if large-bundle construction shows up in profiles.

### E. Resolver allocation

- [x] **8.8** `resolve()` uses one `StringBuilder` per call — confirmed
  in `PatternResolver.resolve`. No nested SB churn on the text path.

- [x] **8.9** `trackPlaceable` uses `mutableSetOf()` → `LinkedHashSet`
  (O(1) average `contains`/`add`/`remove`) for cyclic detection. Fine.

## Estimated effort

~1 day for A (done); ~0.5 day for B–E review (done except 8.6 deferral).
Re-run `:benchmarks:jvmBenchmark` after major resolver/parser changes.
