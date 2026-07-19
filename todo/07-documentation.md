# 07 — Documentation

**Priority: MEDIUM** — No KDoc, no usage guide, no examples. Library is unusable
without docs.

## Tasks

### A. KDoc on public API

- [ ] **7.1** Add KDoc to all public classes/functions in `fluent-bundle`:
  - `FluentBundle` — constructor, `addResource`, `getMessage`, `formatPattern`,
    `formatMessage`, `addFunction`, `setTransform`
  - `FluentResource` — `tryNew`, `entries`
  - `FluentMessage` — `value`, `attribute`
  - `FluentArgs` — construction, `number`, `string`
  - `FluentValue` — sealed class hierarchy
  - `FluentError` — sealed class hierarchy

- [ ] **7.2** Add KDoc to all public classes in `fluent-syntax`:
  - `Entry.Message`, `Entry.Term`, `Entry.Comment`, `Entry.Junk`
  - `Pattern`, `PatternElement`
  - `Expression`, `InlineExpression`
  - `FluentParser.parse()`, `Serializer.serialize()`

- [ ] **7.3** Add KDoc to `fluent-fallback`:
  - `L10n` class
  - `ResourceId`

- [ ] **7.4** Add KDoc to `intl-memoizer`:
  - `LanguageIdentifier.parse()`
  - `IntlFormatters`

### B. Usage guide (in README)

- [ ] **7.5** Quick start section:
  ```kotlin
  // Parse FTL
  val resource = FluentResource.tryNew("""
      greeting = Hello, { $name }!
      unread-emails = { $count ->
          [one] You have one unread email.
         *[other] You have { $count } unread emails.
      }
  """).getOrThrow()

  // Create bundle
  val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
  bundle.addBuiltins()
  bundle.addResource(resource)

  // Format
  val args = FluentArgs.string("name", "World")
  val greeting = bundle.formatMessage("greeting", args)
  // → "Hello, World!"
  ```

- [ ] **7.6** FTL syntax reference (brief, link to Mozilla docs)

- [ ] **7.7** Custom functions guide:
  ```kotlin
  bundle.addFunction("UPPER") { args, _ ->
      FluentValue.Str(args.firstOrNull()?.toString()?.uppercase() ?: "")
  }
  ```

- [ ] **7.8** Fallback chain guide (using `fluent-fallback`)

### C. API reference

- [ ] **7.9** Generate KDoc HTML with Dokka:
  ```kotlin
  plugins {
      id("org.jetbrains.dokka") version "..."
  }
  ```
- [ ] **7.10** Deploy API docs to GitHub Pages

### D. Migration guide

- [ ] **7.11** If coming from fluent-rs, provide a mapping table:
  | Rust API | Kotlin API |
  |----------|-----------|
  | `FluentBundle::new(res, vec!["en-US"])` | `FluentBundle(listOf(LanguageIdentifier.parse("en-US")))` |
  | `bundle.add_resource(res)` | `bundle.addResource(res)` |
  | `bundle.format_pattern(msg, args, errors)` | `bundle.formatPattern(pattern, args, errors)` |

## Estimated effort

~1-2 days for A; ~0.5 day for B; ~0.5 day for C-D.
