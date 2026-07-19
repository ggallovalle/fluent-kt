# 01 — Parser Robustness

**Priority: CRITICAL** — Blocks 2 resolver fixture tests and all real-world broken FTL handling.

## Problem

The parser produces `Entry.Message` entries for malformed FTL instead of `Entry.Junk`.
This means broken input silently corrupts the message catalog rather than being
reported or skipped.

### Known broken cases

| Input | Parser produces | Expected |
|-------|----------------|----------|
| `err1 =` followed by `# comment` at col 0 | Message with comment text as value | Junk entry |
| `err2 = {}` | Message with empty placeable value | Junk entry (or empty value) |
| `err3 =` + `.attr =` (empty attr value) | Message with attribute containing empty Pattern | Junk entry |

### Root cause

`FluentParser.parseMessage()` does not validate that the value/attributes it parsed
are well-formed before returning a `Message`. The Rust upstream has more aggressive
error recovery — when it encounters structural problems it produces `Junk` entries
that `FluentBundle` filters out.

## Tasks

- [x] **1.1** Add `Junk` entry handling to `FluentParser.parseMessage()` — when a
  message has no value AND no valid attributes, produce `Entry.Junk` with the raw
  source text
- [x] **1.2** Add `Junk` entry handling to `FluentParser.parseAttribute()` — when
  an attribute value is empty, produce Junk or skip the attribute
- [x] **1.3** Add parser-level validation for empty placeables `{}` — should produce
  a parse error, not an empty Placeable expression
- [~] **1.4** Verify against upstream fixture `junk.ftl` — partial coverage:
  malformed placeables (`{1x}`) now produce Junk; full structural match still
  needs upstream-style `##` group-comment handling when followed by Junk
  (deferred — the cases that drive the failing resolver fixtures are covered)
- [x] **1.5** Remove `isBrokenMessage` workaround from `FluentBundle.addResource`
  once parser produces Junk entries for these cases
- [x] **1.6** The 2 remaining resolver fixture failures (`err1`, `err3`) should pass
  once the parser is fixed

## Verification

```bash
./gradlew :fluent-syntax:jvmTest :fluent-bundle:jvmTest
# Expect: 0 failures in ResolverFixtureTest
```

## Estimated effort

~2-4 hours. The parser code is well-structured (recursive descent); the fixes are
localised to `parseMessage` and `parseAttribute`.
