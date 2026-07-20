# 09 — Ecosystem Integration

**Priority: LOW** — Core library works standalone. Ecosystem adapters increase
adoption but aren't required for v1.

## Post-v1 order

Gradle plugin → consumer testing helpers → Spring Boot starter → Compose →
CLI (optional).

## Potential integrations

### A. Gradle plugin (highest leverage)

- [x] **9.1** `fluent-codegen` + `fluent-gradle-plugin` (`dev.kbroom.fluent`):
  - `fluentValidate` — Junk + cross-locale ID/arg parity
  - `fluentGenerate` — `*Messages` / `*L10n` + KDoc, `FtlIds`, `*Resources`
  - `fluentScaffoldLocale` — new locale tree from `defaultLocale`
  - Multi-bundle layout `{locale}/{bundle}/**/*.ftl`

  Shared `fluent-codegen` can later back a CLI. Publishing the plugin is still open.

### B. Testing utilities

`fluent-testing` today is **upstream fixture infrastructure** for this repo
(YAML/scenarios), not a consumer test kit. Extend carefully or split surfaces.

- [ ] **9.2** Consumer helpers (new or clearly namespaced in `fluent-testing`):
  - `FluentTestBundle` — preconfigured bundle for app/library tests
  - Assertion helpers: `assertMessage(bundle, id, expected, args)`
  - Keep existing fixture loading for fluent-kt's own tests

### C. Spring Boot starter (JVM-only)

Server-side path of choice here. Thin auto-config over `fluent-bundle` +
`fluent-fallback` + classpath FTL loading — not a rewrite of Spring's
`MessageSource`.

- [ ] **9.3** `fluent-spring-boot-starter` module (JVM):
  - `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - `@ConfigurationProperties("fluent")` for base path, default locale, locales
  - Load `*.ftl` from classpath (e.g. `classpath:i18n/{locale}/*.ftl`)
  - Expose a locale-aware facade bean (prefer `Localization` / small wrapper
    over a single `FluentBundle`)
  - Wire request locale via Spring's `LocaleContextHolder` /
    `AcceptHeaderLocaleResolver` (no custom Accept-Language parser needed)
  - Optional: `FluentMessageSource : MessageSource` bridge for gradual
    migration from `messages.properties`

#### Shape sketch

```yaml
# application.yml
fluent:
  base-path: classpath:i18n
  default-locale: en
  locales: [en, pl, de]
```

```
src/main/resources/i18n/
  en/messages.ftl
  pl/messages.ftl
```

```kotlin
// Auto-config wires something like:
@Component
class FluentMessages(
    private val bundles: Map<Locale, FluentBundle>, // or Localization
    private val defaultLocale: Locale,
) {
    fun format(
        id: String,
        args: FluentArgs? = null,
        locale: Locale = LocaleContextHolder.getLocale(),
    ): String {
        val bundle = bundles[locale] ?: bundles[defaultLocale]
        return bundle?.formatMessage(id, args) ?: id
    }
}
```

```kotlin
// In a controller / service:
@RestController
class GreetingController(private val messages: FluentMessages) {
    @GetMapping("/hello")
    fun hello(@RequestParam name: String): String =
        messages.format("greeting", fluentArgs("name" to name))
}
```

Optional `MessageSource` bridge (for Thymeleaf / existing Spring i18n call sites):

```kotlin
class FluentMessageSource(
    private val messages: FluentMessages,
) : MessageSource {
    override fun getMessage(code: String, args: Array<out Any>?, defaultMessage: String?, locale: Locale): String {
        // Map positional Spring args → FluentArgs named/positional as documented
        return messages.format(code, toFluentArgs(args), locale)
            .takeIf { it != code } ?: (defaultMessage ?: code)
    }
    // … remaining MessageSource overloads
}
```

First cut can ship `FluentMessages` only; add `MessageSource` when someone
needs Thymeleaf / `#{…}` integration.

### D. Android/Jetpack Compose

Needs an Android/Compose target beyond the current JVM + LinuxX64 matrix —
budget more than a thin adapter if wiring that target from scratch.

- [ ] **9.4** `fluent-compose` module — Compose integration:
  ```kotlin
  @Composable
  fun LocalizedText(messageId: String, args: FluentArgs? = null) {
      val bundle = LocalFluentBundle.current
      val text = bundle.formatMessage(messageId, args) ?: messageId
      Text(text)
  }
  ```

- [ ] **9.5** `CompositionLocal` for `FluentBundle`:
  ```kotlin
  val LocalFluentBundle = staticCompositionLocalOf<FluentBundle> {
      error("No FluentBundle provided")
  }
  ```

- [ ] **9.6** Locale change observer — reload bundles when Android locale changes
  (defer; CompositionLocal + format helpers are enough for a first cut)

### E. CLI (optional)

Overlaps with the Gradle plugin. Prefer plugin first; CLI later as a thin
wrapper over the same validate / extract / missing-translation logic.

- [ ] **9.7** `fluent-cli` — validate FTL, pretty-print, extract message IDs,
  check missing translations across locales

## Explicitly out of scope

- **Ktor server i18n** — not planned. Server usage here is Spring; Ktor is
  mostly client-side. Core API is enough without a dedicated module.

## Decision

For v1 release, skip all ecosystem modules. The core library
(`fluent-syntax` + `fluent-bundle` + `fluent-fallback`) should be stable and
well-documented first. Ecosystem work lands in v1.1+ as separate releases, in
the order above.
