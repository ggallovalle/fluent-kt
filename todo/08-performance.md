# 08 — Performance

**Priority: LOW** — No benchmarks exist. Performance is unknown but likely adequate
for typical use cases. This matters at scale (large apps, many locales).

## Current state

No benchmarks. No profiling. No known performance issues but also no measurement.

## Tasks

### A. Microbenchmarks

- [ ] **8.1** Add JMH benchmarks (JVM-only) for:
  - `FluentParser.parse()` — parse 100-message FTL file
  - `FluentBundle.formatMessage()` — format message with args
  - `FluentBundle.formatPattern()` — format pattern with placeables
  - `Serializer.serialize()` — serialize AST to string
  - `FluentResource.tryNew()` — parse + wrap

- [ ] **8.2** Use `kotlinx-benchmark` for KMP-compatible benchmarks:
  ```kotlin
  plugins {
      kotlin("plugin.serialization") version "..."
      id("org.jetbrains.kotlinx.benchmark") version "..."
  }
  ```

### B. Memory profiling

- [ ] **8.3** Measure allocation patterns:
  - `formatPattern` creates a `Scope` per call — check if this can be pooled
  - `PatternElement.TextElement` string concatenation — check if `StringBuilder`
    is efficient
  - `FluentValue.Str` allocation per resolution

- [ ] **8.4** Kotlin/Native memory:
  - `FluentBundle` state should be frozen/immutable after population
  - Verify no accidental mutation after handoff to background threads

### C. Caching

- [ ] **8.5** `IntlLangMemoizer` already caches intl formatters. Verify:
  - Cache hit rate is reasonable
  - No memory leak (unbounded cache growth)
  - Thread-safe on JVM

- [ ] **8.6** Consider caching `formatMessage` results for constant messages
  (messages with no args). Profile before/after.

### D. Bundle population

- [ ] **8.7** `addResource` copies entries into a HashMap. For large bundles
  (1000+ messages), profile:
  - HashMap resize cost
  - Memory overhead per entry
  - Consider `LinkedHashMap` for ordering

### E. Resolver allocation

- [ ] **8.8** `resolve()` uses `StringBuilder` per call — verify no excessive
  allocation. Current implementation seems reasonable (single SB per resolve).

- [ ] **8.9** `trackPlaceable` uses a `MutableSet<String>` — verify O(1) lookups
  for cyclic detection.

## Estimated effort

~1 day for A; ~0.5 day for B-D; profiling is ongoing (run after each major change).
