# 02 — Test Coverage

**Priority: HIGH** — Several modules have zero or minimal tests; critical paths
have no coverage at all.

## Current state

| Module | Test files | Notes |
|--------|-----------|-------|
| fluent-syntax | 4 (ParserFixtureTest, StructuralAstEqualsTest, FluentSyntaxTest, SerializerTest) | Good fixture coverage but structural comparison is partial |
| fluent-bundle | 5 (FluentBundleTest, FunctionTest, TypesTest, ResolverFixtureTest, ResolverExecutionTest) | Resolver fixtures now real; bundle/function tests are thin |
| fluent-fallback | 2 (FallbackTest, FluentFallbackTest) | Basic coverage |
| fluent-pseudo | 1 (PseudoLocaleTest) | Minimal |
| fluent-resmgr | 0 | **No tests at all** |
| fluent-testing | 1 (FixturesTest) | Integration-level only |
| intl-memoizer | 1 (LanguageIdentifierTest) | Only LanguageIdentifier |

## Critical gaps

### A. fluent-resmgr — zero tests

`ResourceManager` has no tests. It manages locale negotiation, resource loading,
and fallback chains — any bug here is invisible.

- [ ] **2.1** Write `ResourceManagerTest` — test `getResource()`, `getBundle()`,
  locale negotiation, missing locale fallback
- [ ] **2.2** Test resource loading from files (JVM-specific `FileSource`)

### B. Serializer round-trip

- [ ] **2.3** Serializer round-trip test: parse → serialize → parse, compare ASTs.
  Currently only tests serialize → string; no round-trip verification
- [ ] **2.4** Add upstream serializer fixtures if available

### C. Resolver edge cases

- [ ] **2.5** Deeply nested placeables (5+ levels) — test for stack overflow
- [ ] **2.6** Very long messages (10K+ chars) — test for performance cliff
- [ ] **2.7** Concurrent access — `FluentBundle` should be safe for multi-thread
  reads after population (Kotlin/Native needs `AtomicReference` or freeze)
- [ ] **2.8** Malicious input — billion laughs, cyclic references exceeding limit,
  deeply nested placeables

### D. Error reporting

- [ ] **2.9** `FluentError` types have no dedicated tests — add tests for each
  error kind (Overriding, NoValue, Reference, Cyclic, Type, Range, Syntax, MissingArg)
- [ ] **2.10** Test that errors are collected correctly through `formatPattern` and
  `formatMessage` APIs

### E. Property-based tests

- [ ] **2.11** Add property-based tests using kotest-property or similar:
  - Any valid identifier round-trips through parse/serialize
  - Any message with no errors formats to a non-null string
  - `addResource` followed by `getMessage` always retrieves the same message

## Verification

```bash
./gradlew jvmTest linuxX64Test
# Expect: all tests green, codecov shows improved coverage
```

## Estimated effort

~1-2 days for A-D; ~0.5 day for E.
