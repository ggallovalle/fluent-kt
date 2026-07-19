# Contributing

Thanks for your interest in fluent-kt. This document covers the workflow
and the project-specific conventions; the load-bearing style and
architecture rules live in [AGENTS.md](AGENTS.md) and apply to every
change.

## Workflow

1. **Open an issue first** for non-trivial changes. Bug reports and small
   cleanups can go straight to a pull request.
2. **Branch off `main`.** Use a descriptive name (`fix/plural-ordinal`,
   `feat/list-builtin`, `docs/readme`).
3. **Write the failing test first.** This project follows strict TDD: the
   test commit and the implementation commit should be separate, in that
   order. See [todo/02-test-coverage.md](todo/02-test-coverage.md) for
   the testing patterns the repo uses.
4. **Verify before requesting review:**
   ```bash
   ./gradlew jvmTest linuxX64Test detektAll
   ```
   All three must be green.
5. **Update the changelog and the relevant todo doc.** Every behavioral
   change belongs in [CHANGELOG.md](CHANGELOG.md). Open work tracked in
   `todo/*.md` should move through the boxes as you land it.

## Code style

The full rules are in [AGENTS.md](AGENTS.md). The ones that bite most often:

- **No wildcard imports** in main source. Tests may use them.
- **Explicit imports** for re-exports — never `typealias` to re-export a
  type from another module.
- **Builder + DSL over mutable singletons.** Public APIs that take
  configuration should be expressed as immutable records produced by a
  builder, with a `fluentXxx { ... }` DSL sugar. Don't hand-roll
  builders when the standard fluent-builder shape already exists.
- **TDD discipline.** Failing test, then implementation, then refactor.
  No "I'll add the test later."

## What goes where

| Concern | Module |
|---|---|
| FTL parser / serializer / AST | `fluent-syntax` |
| Runtime: resolver, bundle, args, types | `fluent-bundle` |
| Locale-aware formatters and memoization | `intl-memoizer` |
| Pseudolocalization | `fluent-pseudo` |
| Fallback chain over multiple bundles | `fluent-fallback` |
| Filesystem resource loading | `fluent-resmgr` |
| Test helpers and shared fixtures | `fluent-testing` |
| Public umbrella re-exports | `fluent` |

If you're not sure where something belongs, open an issue first.

## Pull request review

A PR is reviewable when:

- [ ] `./gradlew jvmTest linuxX64Test detektAll` is green locally.
- [ ] New code has tests covering happy path and at least one error
      path. Security-sensitive code (resolver, parser) needs explicit
      adversarial tests (cyclic, deeply nested, billion-laughs).
- [ ] Public API additions are documented in `CHANGELOG.md`.
- [ ] If the change closes a todo item, the corresponding box in
      `todo/*.md` is checked and the item moved to its status line.

Reviewers will check for: thread-safety claims (back them up with a
concurrency test under `--rerun-tasks`), builder-API consistency
(expressions over statements, `internal` constructors where possible),
and KMP portability (no `java.util.concurrent` in `commonMain`).
