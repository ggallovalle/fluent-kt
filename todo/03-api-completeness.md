# 03 — API Completeness vs fluent-rs

**Priority: HIGH** — Tracks how well fluent-kt's public surface matches
fluent-rs. Most items are now landed (commit cdfb575). Remaining items:
3.6, 3.8, 3.9c, 3.10d — small TDD-shaped test additions.

## Audit: fluent-bundle API

Verified against `fluent-bundle/src/commonMain/kotlin/dev/kbroom/fluent/bundle/FluentBundle.kt`.

| fluent-rs | fluent-kt | Status | Notes |
|-----------|-----------|--------|-------|
| `FluentBundle::new()` | `fluentBundle(locales) { ... }` DSL or `FluentBundleBuilder` | ✅ | Immutable by construction — internal constructor takes the builder. |
| `add_resource()` | `FluentBundleBuilder.addResource()` / `addResourceOverriding()` | ✅ | |
| `add_function()` | `FluentBundleBuilder.addFunction()` (and `function()` DSL sugar) | ✅ | |
| `has_message()` | `FluentBundle.hasMessage(id)` | ✅ | |
| `has_term()` (added later) | `FluentBundle.hasTerm(id)` | ✅ | |
| `has_function()` (added later) | `FluentBundle.hasFunction(id)` | ✅ | |
| `get_message()` | `FluentBundle.getMessage(id)` | ✅ | |
| `get_term()` | `FluentBundle.getTerm(id)` | ✅ | |
| `format_pattern()` | `FluentBundle.formatPattern(pattern, args, errors, rootMessageId)` | ✅ | |
| `format()` (convenience) | `FluentBundle.format(id, args)` → aliases `formatMessage(id, args)` | ✅ | |
| `format_attribute()` | `FluentBundle.formatAttribute(id, attribute, args)` | ✅ | |
| `set_transform()` | `FluentBundleBuilder.setTransform(fn)` + `clearTransform()` + `FluentBundle.getTransform()` | ✅ | |
| `get_entry()` (terms too) | `FluentBundle.getEntry(id)` | ✅ | Unified across messages and terms. |
| Error collection | `FluentError` sealed class | ✅ | |
| `memoizer` access | `FluentBundle.memoizer()` | ✅ | |
| `FluentBundle::add_overriding` | `FluentBundleBuilder.addResourceOverriding()` | ✅ | |
| `FluentBundle::entries` | `FluentBundle.entries(): Map<String, Entry>` | ✅ | |
| `set_use_isolating` | `FluentBundleBuilder.setUseIsolating(value)` | ✅ | Resolver reads the live value per call. |
| `set_formatter` | `FluentBundleBuilder.setFormatter(fn)` | ✅ | |

### DSL sugar (`fluentBundle(locales) { ... }`)

```kotlin
val bundle = fluentBundle(locales = listOf(LanguageIdentifier.parse("en"))) {
    resource("hello = Hello, { $name }!")     // inline FTL
    resource(File("messages.ftl"))             // JVM only
    builtins()                                  // NUMBER, PLURAL, DATETIME, DATE, TIME, LIST, ...
    transform { it.uppercase() }
    function("HELLO") { args, _ -> FluentValue.Str("Hi ${args.firstOrNull()?.asString()}") }
}
```

## Item status

### A. Pattern without bundle (Standalone resolution)

- [x] **3.1** `FluentResource.tryNew(String)` — already takes `String` directly.

### B. Message / Attribute value access

- [x] **3.2** Drop — `FluentMessage.value()` already returns `Pattern?`.
- [x] **3.3** `FluentMessage.attributesMap(): Map<String, Pattern>` added.
- [x] **3.4** `entries()`, `hasTerm()`, `hasFunction()`, `getEntry()` added on `FluentBundle`.

### C. FluentArgs ergonomics

- [x] **3.5** No `FluentArgs.number()` / `.string()` factories. Use `fluentArgsOf("count" to 42)` or `FluentArgs().set("count", 42)`.
- [x] **3.6** `fluentValueOf(FluentType)` round-trip test. ([`FluentValueOfTest`](../../fluent-bundle/src/commonTest/kotlin/dev/kbroom/fluent/bundle/FluentValueOfTest.kt)) — `fluentValueOf(Money)` wraps as `FluentValue.Custom`, `formatMessage` renders via `asString()`.

### D. Built-in functions

- [x] **3.7a** `DATETIME($value, dateStyle?, timeStyle?, hour12?, timeZone?)`.
- [x] **3.7b** `DATE($value, style?, timeZone?)` and `TIME($value, style?, hour12?, timeZone?)`.
- [x] **3.7c** `LIST(values..., type?, style?)` — positional values, named `type`/`style` matching fluent-rs.
- [x] **3.7d** `PLURAL` accepts `ordinal: "true"` named arg (parser doesn't recognize `true`/`false` as BooleanLiteral AST nodes yet; pass as string).
- [x] **3.7e** `NUMBER` accepts named options (`style`, `currency`, `currencyDisplay`, `minimumFractionDigits`, `maximumFractionDigits`, `useGrouping`) matching fluent-rs positional+named convention.
- [x] **3.8** Custom function registration tests ([`CustomFunctionTest`](../../fluent-bundle/src/commonTest/kotlin/dev/kbroom/fluent/bundle/CustomFunctionTest.kt)): (a) a function returning `FluentValue.Custom` (a `FluentType`) renders via `asString()`; (b) a function that throws — the resolver catches and substitutes an error sentinel so the format call doesn't propagate.

### E. Pseudo-localization

- [x] **3.9a** `PseudoMode.Bidi` is RTL-embedding (`U+202B RLE` … `U+202C PDF`). Documented in `PseudoLocale.kt`. Per-character mirroring is out of scope.
- [x] **3.9b** `PseudoMode.Long` added with `longFillChar` and `longFactor`. Distinct from `Widened` (which changes glyphs; `Long` only pads length).
- [x] **3.9c** Wire `createPseudoTransform(PseudoMode.Accented)` into a real `FluentBundle` via `setTransform` and assert a formatted message has accented output ([`PseudoBundleIntegrationTest`](../../fluent-pseudo/src/commonTest/kotlin/dev/kbroom/fluent/pseudo/PseudoBundleIntegrationTest.kt)). Also covers `Long` mode.

### F. Fallback chain

- [x] **3.10a** Class name is `Localization` (not `L10n`). File is `L10n.kt`; the name discrepancy is just a comment in the old todo.
- [x] **3.10b** `Localization.formatAttribute(id, attribute, args)` delegates to `FluentBundle.formatAttribute(id, attribute, args)` (AST path via `FluentMessage.getAttributeValue`).
- [x] **3.10c** `Localization.getAvailableLocales()` and `regenerateBundales()` use `entries().isEmpty()` instead of the stale `hasMessage("")` sentinel.
- [x] **3.10d** Tests for `Localization.formatWithErrors` ([`FormatWithErrorsTest`](../../fluent-fallback/src/commonTest/kotlin/dev/kbroom/fluent/fallback/FormatWithErrorsTest.kt)). Contract: missing-id returns `(null, [])` (no error by design); missing-reference returns `(text, [FluentError.ResolverError(Reference(MESSAGE, missing-id))])`; present message returns `(text, [])`. Implementation required adding `FluentBundle.formatMessageWithErrors(id, args)` to surface resolver errors through `Pair<String?, List<FluentError>>`.

### G. Cross-cutting API inconsistencies

- [x] **3.11** `JvmConcurrentFluentBundle` removed — type system now enforces safety (immutable constructor + thread-safe memoizer via copy-on-write `AtomicRef`).
- [x] **3.12** `setUseIsolating` works (now on the builder; resolver reads the live value per call).

## Status: complete

All items landed. `./gradlew jvmTest linuxX64Test detektAll` green.

Final commit will land the four test files plus the `fluent-pseudo` build dep, the
`FluentBundle.formatMessageWithErrors` method, and the corrected todo.
