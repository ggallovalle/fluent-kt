# 07 — Documentation

**Priority: MEDIUM** — Core entry docs and most public-API KDoc exist.
Remaining work is filling gaps (guides, stubby KDoc, Dokka, migration).

## Current state

| Artifact | Status |
|---|---|
| README quick start + module overview + build | ✅ — uses `fluentBundle { }` / `fluentArgsOf` / `format` |
| LICENSE, CHANGELOG, CONTRIBUTING, AGENTS.md | ✅ (landed under todo/04) |
| KDoc on `FluentBundle` / `FluentBundleBuilder` / DSL | ✅ — class-level usage samples + method docs |
| KDoc on `FluentResource`, AST (`Ast.kt`), `Serializer` | ✅ — solid |
| KDoc on `fluent-pseudo` (`PseudoMode`, options) | ✅ |
| KDoc on `Memoizer` / `LanguageIdentifier` | ✅ (thin but present) |
| KDoc on `FluentArgs` / `FluentError` / `FluentMessage` | ⚠️ — present but mostly one-liners; several `FluentMessage`/`FluentTerm` members undocumented |
| KDoc on `Localization` / fallback / `ResourceManager` | ⚠️ — stubby class/method one-liners |
| KDoc on `IntlFormatters` option enums | ⚠️ — interface documented; most option enums bare |
| Custom-functions / fallback / pseudo / Maven coords in README | ❌ |
| Dokka / GitHub Pages API reference | ✅ — `./gradlew dokkaGenerate`; workflow `.github/workflows/docs.yml` deploys to Pages on `main` |
| fluent-rs → fluent-kt migration table | ❌ |

**Do not** treat this as a greenfield doc project. Prefer deepening stubby
KDoc and extending the README over rewriting what already works.

## Tasks

### A. KDoc coverage / quality pass

- [x] **7.1a** `FluentBundle` + `FluentBundleBuilder` + `fluentBundle` DSL —
  class docs, immutability/thread-safety notes, builder mutators
  (`addResource`, `addFunction`, `addBuiltins`, `setTransform`, …),
  and format APIs (`format`, `formatMessage`, `formatPattern`,
  `formatAttribute`, `formatMessageWithErrors`).
- [x] **7.1b** `FluentResource.tryNew` / entry helpers documented.
- [ ] **7.1c** Deepen `FluentArgs`, `FluentValue` hierarchy, and
  `FluentError` / `EntryKind` — replace one-liners with contracts
  (named vs positional args, when each error fires). Document bare
  `FluentMessage` / `FluentTerm` members (`id`, `value`, `attributes`,
  `getAttribute`, …).
- [x] **7.2a** `fluent-syntax` AST (`Entry.*`, `Pattern`,
  `PatternElement`, `Expression`, `InlineExpression`) and
  `Serializer.serialize` — documented.
- [ ] **7.2b** Public surface of `FluentParser` (constructor / `parse`) —
  file has internal/port notes; ensure the *consumer-facing* entry
  points are clearly KDoc'd (not only the private helpers).
- [ ] **7.3** Deepen `fluent-fallback`: `Localization`,
  `SimpleLocalization`, `ResourceId` / `ResourceType`,
  `BundleGenerator` / iterators, `L10nError` — today mostly stubs.
- [ ] **7.4** Deepen `intl-memoizer`: expand `LanguageIdentifier` /
  `IntlLangMemoizer` where thin; document `IntlFormatters` option
  types and enums used by builtins.
- [ ] **7.4b** Deepen `fluent-resmgr` (`ResourceManager`,
  `CallbackResourceManager`) beyond the one-line class comment.
  `fluent-pseudo` is already in good shape — spot-check only.

### B. Usage guide (README)

- [x] **7.5** Quick start — present and API-correct (DSL, not the old
  mutable `FluentBundle` + `addResource` / `addBuiltins` sequence).
  Keep it the canonical copy-paste path.
- [ ] **7.6** FTL syntax pointer — brief note + link to
  [Mozilla Fluent](https://projectfluent.org/) / syntax guide.
- [ ] **7.7** Custom functions guide (builder / DSL), e.g.:
  ```kotlin
  val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
      resource("hi = { UPPER($name) }")
      function("UPPER") { args, _ ->
          FluentValue.Str(args.firstOrNull()?.toString()?.uppercase() ?: "")
      }
  }
  ```
- [ ] **7.8** Fallback chain guide (`Localization` /
  `SimpleLocalization` from `fluent-fallback`).
- [ ] **7.8b** Pseudolocalization + `createPseudoTransform` /
  `setTransform` example (module mentioned in overview only).
- [ ] **7.8c** Maven Central coordinates for consumers
  (`io.github.ggallovalle:fluent:…` / module artifacts). SNAPSHOT is
  live; update when `0.1.0` ships (see todo/05 §E.6, todo/06).
- [ ] **7.8d** ~~Fix broken relative link in README status table~~
  (fixed while wiring Dokka: `todo/04-…`).
- [x] **7.8e** README "API reference" section linking to
  `https://ggallovalle.github.io/fluent-kt/`.

### C. API reference

- [x] **7.9** Dokka 2.2.0 wired at root: aggregates publishing modules
  (excludes `:fluent-testing`). `./gradlew dokkaGenerate` →
  `build/dokka/html`.
- [x] **7.10** GitHub Pages workflow (`.github/workflows/docs.yml`)
  builds Dokka and deploys via `actions/deploy-pages`. Requires repo
  Settings → Pages → Source: **GitHub Actions** (one-time). Goes live
  after the workflow runs on `main` (or via `workflow_dispatch`).

### D. Migration guide

- [ ] **7.11** fluent-rs → fluent-kt mapping table using **current**
  APIs (builder/DSL, not the deleted mutable bundle). Draft:

  | Rust (fluent-rs) | Kotlin (fluent-kt) |
  |---|---|
  | `FluentBundle::new(locales)` + `add_resource` | `fluentBundle(locales) { resource(...) }` or `FluentBundleBuilder` |
  | `bundle.add_function` / builtins | `function(...)` / `builtins()` on builder |
  | `bundle.format_pattern(…)` | `bundle.formatPattern(…)` |
  | `bundle.get_message` / `format` | `getMessage` / `format` / `formatMessage` |
  | `Localization` fallback chain | `dev.kbroom.fluent.fallback.Localization` |

## Estimated effort remaining

~0.5–1d for A (KDoc quality pass); ~0.5d for remaining B guides + D
migration. Dokka + Pages (C) are wired.
