# fluent-kt

A Kotlin Multiplatform port of [Mozilla's Fluent](https://projectfluent.org/),
the localization system that powers Firefox, Thunderbird, and Mozilla
VPN. fluent-kt parses `.ftl` files, resolves message references and
plural/ordinal variants, and renders messages with locale-aware number,
date, and list formatting.

## Status

| Target | Supported |
|---|---|
| JVM | ✅ |
| Linux x64 (Kotlin/Native) | ✅ |
| macOS, iOS, JS, wasm | ❌ — not yet (see [todo/04](todo/04-project-hygiene.md)) |

## API reference

Generated KDoc (Dokka) is published to GitHub Pages:

**https://ggallovalle.github.io/fluent-kt/**

Build locally with `./gradlew dokkaGenerate` (output under `build/dokka/html`).

## Quick start

```kotlin
import dev.kbroom.fluent.bundle.fluentBundle
import dev.kbroom.fluent.bundle.fluentArgsOf
import dev.kbroom.fluent.intl.LanguageIdentifier

// Construct a bundle from inline FTL via the DSL.
val bundle = fluentBundle(
    locales = listOf(LanguageIdentifier.parse("en-US")),
) {
    resource("""
        greet = Hello, { $name }!
        unread-emails = { $count ->
            [one] You have one unread email.
           *[other] You have { $count } unread emails.
        }
    """.trimIndent())
    builtins()  // NUMBER, PLURAL, DATETIME, DATE, TIME, LIST, ...
}

bundle.format("greet", fluentArgsOf("name" to "World"))
// → "Hello, World!"

bundle.format("unread-emails", fluentArgsOf("count" to 5))
// → "You have 5 unread emails."

bundle.format("unread-emails", fluentArgsOf("count" to 1))
// → "You have one unread email."
```

## Bundles are immutable

A `FluentBundle` is built once via the DSL or `FluentBundleBuilder` and
never mutated afterwards. Concurrent reads from multiple threads are
safe by construction — no locks, no `seal()`, no per-call setup. See
the [FluentBundle](../fluent-bundle/src/commonMain/kotlin/dev/kbroom/fluent/bundle/FluentBundle.kt)
class for details.

## Module overview

| Module | Purpose |
|---|---|
| `fluent-syntax` | Parser and serializer for FTL → AST. Independent of bundle/runtime. |
| `intl-memoizer` | Locale-aware formatters (numbers, dates, lists) with thread-safe cache. |
| `fluent-bundle` | `FluentBundle` runtime: resolver, message/term lookup, custom functions, builder DSL. The main API surface. |
| `fluent-pseudo` | Pseudolocalization (`Accented`, `Bidi`, `Long`, `Widened`, `Hidden` modes) for catching hard-coded strings. |
| `fluent-fallback` | `Localization` facade: locale chain with per-locale fallback (e.g. `en-US` → `en` → default). |
| `fluent-resmgr` | File-system resource loading for bundles, with locale-tag → language → base path resolution. |
| `fluent-testing` | Test helpers: loader for upstream `fluent-rs` fixtures. |
| `fluent` | Umbrella module re-exporting the common entry points. |

## Build

```bash
./gradlew jvmTest          # JVM unit tests
./gradlew linuxX64Test     # Native tests
./gradlew detektAll        # Style + lint
./gradlew build            # All artifacts
```

Requires JDK 21 (Gradle target version).

## License

[MIT](LICENSE) — same as fluent-rs's MIT option.

## Acknowledgements

- [Mozilla Fluent](https://projectfluent.org/) — the original Rust
  implementation this project is modeled after.
- [fluent-rs](https://github.com/projectfluent/fluent-rs) — reference
  for test fixtures and parser semantics.
