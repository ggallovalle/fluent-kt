# 03 — API Completeness vs fluent-rs

**Priority: HIGH** — The audit table at the top is stale: several rows marked
✅ are not actually implemented (`getEntry`, `entries()`), and several items
listed as "missing" are actually present (`FluentMessage.value()` already
returns `Pattern?`, `FluentResource.tryNew` already takes `String`). The
missing-builtins section misses DATETIME/DATE/TIME/LIST entirely. This
revision rewrites the audit against the current code.

## Audit: fluent-bundle API

Verified against `fluent-bundle/src/commonMain/kotlin/dev/kbroom/fluent/bundle/FluentBundle.kt`.

| fluent-rs | fluent-kt | Status | Notes |
|-----------|-----------|--------|-------|
| `FluentBundle::new()` | `FluentBundle(locales, useIsolating)` | ✅ | |
| `add_resource()` | `addResource()` / `addResourceOverriding()` | ✅ | |
| `add_function()` | `addFunction()` | ✅ | |
| `has_message()` | `hasMessage(id)` | ✅ | No `hasTerm` / `hasFunction`. |
| `get_message()` | `getMessage(id)` | ✅ | |
| `format_pattern()` | `formatPattern(pattern, args, errors, rootMessageId)` | ✅ | |
| `format()` (convenience) | `format(id, args)` → aliases `formatMessage(id, args)` | ✅ | `formatMessage` is the canonical name; `format` is the alias (todo got the relationship reversed). |
| `set_transform()` | `setTransform(fn)` + `clearTransform()` + `getTransform()` | ✅ | |
| `get_entry()` (terms too) | `getMessage(id)` + `getTerm(id)` (separate) | ⚠️ | No unified `getEntry(id): Entry?`. Todo table falsely marked ✅. |
| Error collection | `FluentError` sealed class | ✅ | |
| `memoizer` access | `memoizer()` | ✅ | |
| `FluentBundle::add_overriding` | `addResourceOverriding()` | ✅ | |
| **`FluentBundle::entries`** | **none** | ❌ | `entries: MutableMap<String, Entry>` is `private`. No public `entries()`, no iteration over the catalog. |
| `set_use_isolating` | `setUseIsolating(value)` — **no-op** | ❌ | The setter exists but is `@Suppress("UnusedParameter")` and does nothing (comment in source acknowledges the limitation). Either make it mutate `useIsolating` or remove it. |
| `get_message` for attribute | `getMessage(id)` + `FluentMessage.getAttributeValue(name)` | ✅ | No top-level `formatAttribute(id, name, args)` on `FluentBundle` — only on `Localization`. |

## Missing features

### A. Pattern without bundle (Standalone resolution)

- [x] **3.1** `FluentResource.tryNew(String)` — already takes `String` directly.
  No work. Removed from this section.

### B. Message / Attribute value access

- [ ] **3.2** Drop: `FluentMessage.value()` already returns `Pattern?` (see
  `FluentMessage.kt:14`). The TODO's claim that it "currently returns the
  resolved string" is wrong. No work needed.

- [ ] **3.3** Add `FluentMessage.attributes()` resolved view.
  Current: `attributes(): List<Attribute>` (raw AST nodes).
  Needed: a convenience that returns `Map<String, Pattern?>` keyed by
  attribute name (or `List<Pair<String, Pattern?>>` to preserve order)
  for tooling that just wants the catalog. Keep the AST accessor too.
  Note: `getAttributeValue(name)` already resolves a single attribute, so
  this is just a bulk variant. Add a test.

- [ ] **3.4** **Expose `entries()` on `FluentBundle`.** `entries` is currently
  `private`. Add:
  - `fun entries(): Map<String, Entry>` — snapshot copy (defensive against
    concurrent mutation, matching fluent-rs semantics).
  - `fun hasTerm(id: String): Boolean` — mirrors `hasMessage`.
  - `fun hasFunction(id: String): Boolean` — complements `getFunction`.
  Tests: assert snapshot returns the expected types (`Entry.Message`,
  `Entry.Term`), assert mutations after `entries()` doesn't affect the
  bundle.

### C. FluentArgs ergonomics

- [x] **3.5** (revised) Drop the `FluentArgs.number(...)` / `.string(...)`
  assertion. They don't exist; the canonical construction is
  `fluentArgsOf("count" to 42)` or `FluentArgs().set("count", 42)`.
  Verify both paths work end-to-end and add a test asserting that
  `FluentValue.Number` is passed through to the resolver (not silently
  coerced to a string).

- [ ] **3.6** External argument support — `fluentValueOf(value: Any?)`
  covers `FluentType` → `FluentValue.Custom` and the numeric primitive
  promotions. Verify with a `FluentType` impl test that a custom value
  round-trips through `formatMessage` and renders via `asString()`. Add
  a fixture-style test for an `EmailAddress` or `Money` `FluentType`.

### D. Built-in functions — **DATETIME / DATE / TIME / LIST are missing**

`addBuiltins()` only registers NUMBER, PLURAL, CONCAT, SUM, IDENTITY
(see `FluentBundle.kt:248-254`). The `PlatformIntl` layer already
implements `formatDateTime`, `formatDate`, `formatTime`, and `formatList`,
but `addBuiltins()` does not expose them. This is the biggest hole in
the table.

- [ ] **3.7a** Implement and register `DATETIME(\$value, options)` —
  passes a `Long` epoch-millis value and an options map
  (`dateStyle`, `timeStyle`, `hour12`, `timeZone`) through to
  `IntlHelpers.formatDateTime`. Returns `FluentValue.Str` or
  `FluentValue.Error("DATETIME requires a number argument")`.

- [ ] **3.7b** Register `DATE(\$value, style?, timeZone?)` and
  `TIME(\$value, style?, hour12?, timeZone?)` calling `formatDate` /
  `formatTime`. (Optional but small — split from 3.7a.)

- [ ] **3.7c** Register `LIST(values..., type?, style?)` calling
  `IntlHelpers.formatList`. Validate that fluent-rs's positional-vs-named
  semantics match (positional values, named type/style).

- [ ] **3.7d** Extend `PLURAL` to support an `ordinal: true` named
  argument that switches the category function from cardinal to ordinal
  plural rules. The fluent-rs upstream does this; ours doesn't. Add a
  test covering both cardinal and ordinal outputs for `en` (1st vs 1).

- [ ] **3.7e** Extend `NUMBER` to accept `style`, `currency`,
  `currencyDisplay`, `minimumFractionDigits`, `maximumFractionDigits`,
  `useGrouping` as **named options** (e.g.
  `NUMBER($n, style: "currency", currency: "USD")`) rather than the
  current positional-options trick. The current positional handling
  (`if (args.size > 1) ...`) is fragile and undocumented.

- [ ] **3.8** Custom function registration — `addFunction(id, fn)` is
  ergonomic. Add a test that a custom function returning a `FluentValue.Custom`
  (a `FluentType`) renders correctly via `asString()`, and one that
  throws — the resolver should catch the exception and return
  `FluentValue.Error("Function error: ...")`.

### E. Pseudo-localization

- [ ] **3.9a** Bidi mode is **RTL-embedding** (`\u202B...\u202C`), not a
  full bidi pseudo that mirrors the string. If that's intentional, document
  it. If not, switch to wrapping in RLM (`\u200F`) + per-character mirroring.
  Decide and document. Currently `PseudoLocaleTest.bidi mode` only asserts
  the wrapper exists — it does not verify mirroring.

- [ ] **3.9b** No `Long` pseudo mode. Upstream `fluent-pseudo` has
  `Accent`, `Bidi`, `Long` (pad with filler characters). Our impl has
  `Accented`, `Bidi`, `Widened`, `Hidden`. Decide whether `Widened`
  covers the `Long` use case (it doesn't — it changes glyphs, not length).
  Either rename `Widened` → add `Long(padChar, factor)` mode, or document
  that `Widened` is the closest equivalent and `Long` is out of scope.

- [ ] **3.9c** Add a fixture-style test that wires `createPseudoTransform`
  to a real `FluentBundle.setTransform` and asserts a formatted message
  has accented output. Currently `PseudoLocaleTest` tests the transform
  in isolation, not the bundle integration.

### F. Fallback chain

- [ ] **3.10a** `Localization` class exists (todo calls it `L10n` —
  wrong; `L10n.kt` is the file, `Localization` is the class). Document
  the actual name. Fallback chain works.

- [ ] **3.10b** `Localization.formatAttribute(id, attribute, args)`
  composes `"$id.$attribute"` and forwards to `bundle.format`. This
  works only for messages with dot-allowed ids and ignores the dedicated
  attribute pipeline. Replace with a `bundle.formatAttribute` that uses
  `FluentMessage.getAttributeValue` (already exists).

- [ ] **3.10c** `Localization.getAvailableLocales()` uses
  `bundle.hasMessage("")` as a sentinel for "this bundle has any
  content". That sentinel was removed from upstream fluent-rs years ago
  and is unreliable. Replace with a real `FluentBundle.isEmpty()` (or
  expose `entries().isEmpty()` once 3.4 lands).

- [ ] **3.10d** Add a `formatWithErrors` test that asserts missing-message
  behavior returns `(null, [FluentError.ResolverError(Reference)])` and
  that the missing-message contract doesn't throw.

### G. Cross-cutting API inconsistencies

- [ ] **3.11** `JvmConcurrentFluentBundle` (`jvmMain`) only forwards a
  subset of `FluentBundle` API: `format`, `formatPattern`, `addResource`,
  `addResourceOverriding`, `getMessage`, `hasMessage`, `addBuiltins`,
  `locales`, `addFunction`, `setTransform`, `memoizer`. Missing:
  `getTerm`, `hasTerm`, `getFunction`, `hasFunction`, `clearTransform`,
  `getTransform`, `formatWithErrors`, `setUseIsolating`, `entries`.
  Forward the full surface or document the deliberate gap.

- [ ] **3.12** Either implement `setUseIsolating` (making `useIsolating`
  a `var` and threading the new value through the resolver's
  `scope.bundle.useIsolating` read) or remove the dead method. Currently
  it silently lies.

## Estimated effort

~3-5 days. 3.7 (missing builtins) and 3.11 (concurrent bundle surface)
are the largest items; 3.4 and 3.10 are small but touch public API
shape.

## Open questions

- Should `FluentArgs` grow `number()` / `string()` factory methods, or
  is `fluentArgsOf("name" to value)` enough? (Resolved: lean on the
  named-arg helper; don't add redundant factories unless a real consumer
  needs them.)
- Should `Widened` pseudo mode be renamed/extended to cover `Long`?
- Should `JvmConcurrentFluentBundle` be removed in favor of documenting
  `FluentBundle` as thread-safe-for-reads (after making the `entries`
  map internal-mutation-safe)?

## Verification

```bash
./gradlew jvmTest linuxX64Test detektAll
# All existing suites must remain green; new tests for 3.3, 3.4, 3.7a-c,
# 3.10b-d must pass.
```
