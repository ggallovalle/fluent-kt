# 09 — Ecosystem Integration

**Priority: LOW** — Core library works standalone. Ecosystem adapters increase
adoption but aren't required for v1.

## Potential integrations

### A. Android/Jetpack Compose

- [ ] **9.1** `fluent-compose` module — Compose integration:
  ```kotlin
  @Composable
  fun LocalizedText(messageId: String, args: FluentArgs? = null) {
      val bundle = LocalFluentBundle.current
      val text = bundle.formatMessage(messageId, args) ?: messageId
      Text(text)
  }
  ```

- [ ] **9.2** `CompositionLocal` for `FluentBundle` (locale-aware):
  ```kotlin
  val LocalFluentBundle = staticCompositionLocalOf<FluentBundle> {
      error("No FluentBundle provided")
  }
  ```

- [ ] **9.3** Locale change observer — reload bundles when Android locale changes

### B. Ktor

- [ ] **9.4** `fluent-ktor` module — server-side i18n:
  ```kotlin
  install(FluentI18n) {
      defaultLocale = "en"
      resources = listOf("messages/en.ftl", "messages/pl.ftl")
  }
  // In route handler:
  val greeting = call.localized("greeting", "name" to "World")
  ```

- [ ] **9.5** Accept-Language header parsing → `LanguageIdentifier`

### C. CLI

- [ ] **9.6** `fluent-cli` module — command-line tool for:
  - Validating FTL files (parse + error reporting)
  - Serializing FTL (pretty-print)
  - Extracting message IDs
  - Checking for missing translations across locales

### D. Gradle plugin

- [ ] **9.7** `fluent-gradle-plugin` module (currently empty shell):
  - Task to validate FTL files in src/main/resources
  - Task to generate Kotlin constants for message IDs
  - Task to check for missing translations

### E. Testing utilities

- [ ] **9.8** `fluent-testing` module already exists. Verify it provides:
  - `FluentTestBundle` — pre-configured bundle for tests
  - Fixture loading from YAML (already implemented)
  - Assertion helpers: `assertMessage(bundle, id, expected, args)`

## Estimated effort

Each integration is ~1-2 days. For v1, A and E are highest value. B-D are
post-v1 unless there's a specific use case.

## Decision

For v1 release, skip all ecosystem modules. The core library
(`fluent-syntax` + `fluent-bundle` + `fluent-fallback`) should be stable and
well-documented first. Ecosystem adapters can be added in v1.1+ as separate
artifacts.
