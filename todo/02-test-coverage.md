# 02 — Test Coverage

**Priority: HIGH** — Several modules have zero or minimal tests; critical paths
have no coverage at all.

## Current state

| Module | Test files | Notes |
|--------|-----------|-------|
| fluent-syntax | 7 (ParserFixtureTest, StructuralAstEqualsTest, FluentSyntaxTest, SerializerTest, SerializerRoundTripTest, ParseJunkMessagesTest, NestedPlaceablesTest) | Structural round-trip + nested placeables added |
| fluent-bundle | 6 (FluentBundleTest, FunctionTest, TypesTest, ResolverFixtureTest, ResolverExecutionTest, FluentErrorTest) | FluentErrorTest covers each error kind |
| fluent-fallback | 2 (FallbackTest, FluentFallbackTest) | Basic coverage |
| fluent-pseudo | 1 (PseudoLocaleTest) | Minimal |
| fluent-resmgr | 1 (ResourceManagerTest) | **Now covered**: getBundle/getBundles + locale-tag then language then base fallback chain |
| fluent-testing | 1 (FixturesTest) | Integration-level only |
| intl-memoizer | 1 (LanguageIdentifierTest) | Only LanguageIdentifier |

## Critical gaps

### A. fluent-resmgr — zero tests ✅

- [x] **2.1** Write `ResourceManagerTest` — test `getResource()`, `getBundle()`,
  locale negotiation, missing locale fallback
- [x] **2.2** Test resource loading from files (JVM-specific `FileSource`)
  — exercised indirectly through `MockFs` overriding `readFile`

### B. Serializer round-trip ✅

- [x] **2.3** Serializer round-trip test: parse → serialize → parse, compare ASTs.
  Three curated fixtures + four targeted cases (placeable, term-attribute,
  select-shape, junk-content).
- [~] **2.4** Add upstream serializer fixtures if available — full
  fixture-wide structural round-trip surfaced pre-existing serializer bugs
  (VariantKey data-class toString, named-arg `NumberLiteral(value=N)`
  literals, missing `*` default-variant prefix). Tracked as follow-up.

### C. Resolver edge cases

- [x] **2.5** Deeply nested placeables (5+ levels) — `NestedPlaceablesTest`
  asserts 1, 5, and 10 levels.
- [ ] **2.6** Very long messages (10K+ chars) — test for performance cliff
- [ ] **2.7** Concurrent access — `FluentBundle` should be safe for multi-thread
  reads after population (Kotlin/Native needs `AtomicReference` or freeze)
- [ ] **2.8** Malicious input — billion laughs, cyclic references exceeding limit,
  deeply nested placeables. Requires implementing the limits before a test
  can assert them; deferred as a separate scope.

### D. Error reporting ✅

- [x] **2.9** `FluentError` types have no dedicated tests — `FluentErrorTest`
  covers Overriding, ParserError, ResolverError wrapper, and each
  ResolverError kind (NoValue, Cyclic, MissingDefault, TooManyPlaceables,
  Reference × 4 ReferenceKinds).
- [x] **2.10** Test that errors are collected correctly through `formatPattern`
  and `formatMessage` APIs — `formatPattern` Reference-error collection
  test passes; `formatMessage` null/missing-message contract tested.

### E. Property-based tests ✅

- [x] **2.11** Add property-based tests using kotest-property or similar.
  Adds kotest-property 5.9.1 to fluent-syntax and fluent-bundle
  commonTest. `PropertyTest` (fluent-syntax) drives the parser
  invariant "any valid identifier round-trips" + "parsing is
  referentially transparent" across hundreds of generated inputs.
  `BundlePropertyTest` (fluent-bundle) drives "addResource then
  getMessage retrieves the same message" + the inverse
  "getMessage returns null for any id NOT added". Generators
  constrain to inputs the current implementation accepts; broken-input
  cases remain in ParseJunkMessagesTest.

## Verification

```bash
./gradlew jvmTest linuxX64Test detektAll
# All green. 35+ suites. Per-module test totals (jvm):
# fluent-syntax: 8 test suites, 30+ cases
# fluent-bundle: 6 test suites, 35+ cases including 49-failure ResolverFixtureTest
# fluent-resmgr: 1 test suite, 6 cases
```

## Estimated effort

~1-2 days for A-D; ~0.5 day for E.
