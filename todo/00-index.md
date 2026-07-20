# fluent-kt Production-Grade v1 — TODO Index

Priority-ordered list of work items to reach a publishable v1.0.0.

## Priority order

| # | File | Priority | Effort | Blocks |
|---|------|----------|--------|--------|
| 01 | [Parser Robustness](01-parser-robustness.md) | CRITICAL | 2-4h | 2 test failures, Junk entry handling |
| 02 | [Test Coverage](02-test-coverage.md) | HIGH | 1-2d | Confidence for release |
| 03 | [API Completeness](03-api-completeness.md) | HIGH | 1-2d | Feature parity |
| 04 | [Project Hygiene](04-project-hygiene.md) | HIGH | 0.5-1.5d | README, LICENSE, dead code |
| 05 | [CI/CD](05-ci-cd.md) | HIGH | mostly done | Automated quality gates; SNAPSHOT live; first release pending |
| 06 | [Publishing](06-publishing.md) | MEDIUM | ~0.5d left | SNAPSHOT live; cut `0.1.0` |
| 07 | [Documentation](07-documentation.md) | MEDIUM | ~1d left | Dokka+Pages wired; KDoc/guides gaps remain |
| 08 | [Performance](08-performance.md) | LOW | mostly done | `:benchmarks` JMH; 8.6 deferred |
| 09 | [Ecosystem](09-ecosystem.md) | LOW | 3-10d | Gradle plugin, testing, Spring Boot, Compose |

## Critical path to v1.0.0

```
01 → 04 → 05 → 06 → v1.0.0-RC1
         ↓
        02 → 03 → 07 → v1.0.0
```

### Minimum viable v1 (01 + 04 + 05 + 06)
1. Fix parser to produce Junk entries → all fixture tests green
2. Add README + LICENSE + remove dead dirs
3. Add GitHub Actions CI
4. Maven Central publishing — SNAPSHOT live; cut first `0.1.0` release

### Full v1 (+ 02 + 03 + 07)
5. Fill test coverage gaps (resmgr, serializer round-trip, edge cases)
6. Verify API completeness vs fluent-rs
7. Add KDoc to public API + usage guide

### Post-v1 (08 + 09)
8. Benchmarks — `:benchmarks` module landed; re-run after major hot-path changes
9. Ecosystem adapters (when adoption matters)

## Current snapshot

- **Commits**: 17 (since session start)
- **Tests**: all suites green across JVM + LinuxX64 — property-based tests via
  kotest-property exercise parser round-trip and bundle getMessage invariants;
  structural round-trip across curated fixtures; broken-Junk fixture covers
  err1/err3.
- **Modules**: 8 (fluent-syntax, fluent-bundle, fluent-fallback, fluent-pseudo, fluent-resmgr, fluent-testing, fluent, intl-memoizer)
- **Dead modules**: 4 (fluent-compiler, fluent-compiler-annotations, fluent-gradle-plugin, fluent-sample)
- **Targets**: JVM + LinuxX64
- **Lint**: detekt clean
