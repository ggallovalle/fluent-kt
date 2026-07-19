# fluent-kt Production-Grade v1 — TODO Index

Priority-ordered list of work items to reach a publishable v1.0.0.

## Priority order

| # | File | Priority | Effort | Blocks |
|---|------|----------|--------|--------|
| 01 | [Parser Robustness](01-parser-robustness.md) | CRITICAL | 2-4h | 2 test failures, Junk entry handling |
| 02 | [Test Coverage](02-test-coverage.md) | HIGH | 1-2d | Confidence for release |
| 03 | [API Completeness](03-api-completeness.md) | HIGH | 1-2d | Feature parity |
| 04 | [Project Hygiene](04-project-hygiene.md) | HIGH | 0.5-1.5d | README, LICENSE, dead code |
| 05 | [CI/CD](05-ci-cd.md) | HIGH | 1-1.5d | Automated quality gates |
| 06 | [Publishing](06-publishing.md) | MEDIUM | 1-1.5d | Library consumption |
| 07 | [Documentation](07-documentation.md) | MEDIUM | 1.5-2.5d | Usability |
| 08 | [Performance](08-performance.md) | LOW | 1-1.5d | Benchmarks, profiling |
| 09 | [Ecosystem](09-ecosystem.md) | LOW | 3-10d | Compose, Ktor, CLI adapters |

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
4. Configure Maven Central publishing

### Full v1 (+ 02 + 03 + 07)
5. Fill test coverage gaps (resmgr, serializer round-trip, edge cases)
6. Verify API completeness vs fluent-rs
7. Add KDoc to public API + usage guide

### Post-v1 (08 + 09)
8. Benchmarks (when performance matters)
9. Ecosystem adapters (when adoption matters)

## Current snapshot

- **Commits**: 13 (since session start)
- **Tests**: all suites green — ResolverFixtureTest no longer skips err1/err3; ParserFixtureTest passes; StructuralAstEqualsTest covers junk.ftl no-Message invariant
- **Modules**: 8 (fluent-syntax, fluent-bundle, fluent-fallback, fluent-pseudo, fluent-resmgr, fluent-testing, fluent, intl-memoizer)
- **Dead modules**: 4 (fluent-compiler, fluent-compiler-annotations, fluent-gradle-plugin, fluent-sample)
- **Targets**: JVM + LinuxX64
- **Lint**: detekt clean
