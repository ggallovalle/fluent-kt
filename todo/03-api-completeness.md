# 03 — API Completeness vs fluent-rs

**Priority: HIGH** — Several public API features from fluent-rs are missing or
incomplete in fluent-kt.

## Audit: fluent-bundle API

| fluent-rs | fluent-kt | Status |
|-----------|-----------|--------|
| `FluentBundle::new()` | `FluentBundle(locales, useIsolating)` | ✅ |
| `add_resource()` | `addResource()` / `addResourceOverriding()` | ✅ |
| `add_function()` | `addFunction()` | ✅ |
| `has_message()` | `hasMessage()` | ✅ |
| `get_message()` | `getMessage()` | ✅ |
| `format_pattern()` | `formatPattern()` | ✅ |
| `format()` (convenience) | `formatMessage()` | ✅ |
| `set_transform()` | `setTransform()` | ✅ |
| `get_entry()` (terms too) | `getEntry()` | ✅ |
| Error collection | `FluentError` sealed class | ✅ |
| `memoizer` access | `memoizer()` | ✅ |
| **`FluentBundle::add_overriding`** | `addResourceOverriding()` | ✅ |
| **`FluentBundle::entries`** | `entries()` | ✅ |
| **`FluentBundle::locales`** | `locales()` | ✅ |

## Missing features

### A. Pattern without bundle (Standalone resolution)

- [ ] **3.1** `FluentResource.tryNew()` should accept `String` directly (currently
  requires wrapping). Check if already done — verify the API surface.

### B. Message/Attribute value access

- [ ] **3.2** `FluentMessage.value()` should return `Pattern?` — currently returns
  the resolved string. Need to expose the raw pattern for tools that want AST access
  without resolving.
- [ ] **3.3** `FluentMessage.attributes()` should return attribute names and
  resolved values (currently only `attribute()` with single name)

### C. FluentBundle iteration

- [ ] **3.4** Expose `entries` as a `Map<String, Entry>` or provide iteration.
  Currently `entries()` returns a copy but is it typed correctly?

### D. Variable/argument passing

- [ ] **3.5** `FluentArgs` is a thin wrapper around `Map<String, FluentValue>`.
  Verify:
  - Numeric args: `FluentArgs.number("count", 42)`
  - String args: `FluentArgs.string("name", "World")`
  - Direct map construction
- [ ] **3.6** External argument support (passing custom `FluentValue` types for
  app-specific formatting) — verify this works end-to-end

### E. Functions

- [ ] **3.7** Built-in functions (NUMBER, PLURAL, CONCAT, SUM, IDENTITY) are
  implemented. Verify they match upstream behavior:
  - NUMBER with currency options
  - PLURAL with ordinal
  - CONCAT with mixed types
- [ ] **3.8** Custom function registration — verify the `addFunction` API is
  ergonomic and works with all value types

### F. Pseudo-localization

- [ ] **3.9** `fluent-pseudo` module exists but needs verification. Upstream has
  `PseudoLocalization` that wraps strings for testing. Verify:
  - Accented pseudo (adds diacritics)
  - Bidi pseudo (adds bidi markers)
  - Long pseudo (extends string length)

### G. Fallback chain

- [ ] **3.10** `fluent-fallback` module exists. Verify:
  - `L10n` class is the main entry point
  - Fallback chain: `en-US` → `en` → default
  - Missing message returns null (not throw)
  - Missing locale in chain is handled gracefully

## Estimated effort

~1-2 days. Most items are verification + small fixes.
