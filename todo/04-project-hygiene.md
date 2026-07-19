# 04 — Project Hygiene

**Priority: HIGH** — Tracks the structural changes needed before fluent-kt
can be published. JVM + LinuxX64 are the only supported targets today.

## Status

All items in sections A, B, C, D, F are landed. Section E (Kotlin target
expansion) is intentionally deferred — see the section below for why.

### A. Missing essential files

- [x] **4.1** **README.md** — Quick start, module overview, build
  instructions, status table.
- [x] **4.2** **LICENSE** — MIT (fluent-rs is dual-licensed under MIT and
  Apache-2.0; we picked MIT).
- [x] **4.3** **CHANGELOG.md** — `Keep a Changelog` format, one `[Unreleased]`
  entry covering all the API-completeness work from commits `cdfb575`
  and `46fa290`.
- [x] **4.4** **CONTRIBUTING.md** — Workflow, code-style pointers to
  `AGENTS.md`, module ownership table, PR review checklist.

### B. Dead directories to remove

- [x] **4.5** `fluent-compiler/` — deleted.
- [x] **4.6** `fluent-compiler-annotations/` — deleted.
- [x] **4.7** `fluent-gradle-plugin/` — deleted.
- [x] **4.8** `fluent-sample/` — deleted.

### C. Module description in build.gradle.kts

- [x] **4.9** All 8 modules have a top-level `description` field for Maven
  POM generation.

### D. Gradle wrapper and dependencies

- [x] **4.10** Gradle wrapper is at `9.6.0` — current.
- [x] **4.11** Versions in `gradle/libs.versions.toml` are current:
  Kotlin 2.4.0, kotlinx-serialization 1.11.0, atomicfu 0.32.1, detekt
  2.0.0-alpha.5 (pinned alpha), kotest 5.9.1.
- [x] **4.12** Removed unused `kotlinx-datetime` dependency from every
  module and from the version catalog. Zero source usages; safe removal.
  `kotlinx-serialization-json` retained where `@Serializable` is used
  (intl-memoizer, fluent-syntax, fluent-testing).

### E. Kotlin targets

- [ ] **4.13** Deferred. Current targets: JVM + LinuxX64. macOS, iOS,
  JS, and wasm targets are **not** in scope for this todo. Reasons:
  - macOS / iOS targets require an Apple toolchain to verify (no
    CI runner on hand); shipping untested targets invites silent
    breakage.
  - JS / wasmJs need a different test runner and a
    `PlatformIntl`-equivalent for the JS stdlib (the current
    `PlatformIntl.formatDateTime` calls into `java.time` / libc).
  - No users asking for additional targets yet.

  Reopen this todo when there's a concrete consumer. To add a target
  later: enable the target in each module's `kotlin {}` block, add a
  matching `PlatformIntl` implementation, and run `./gradlew
  <target>Test` to verify.

### F. .gitignore cleanup

- [x] **4.14** `.gitignore` covers `.gradle/`, `build/`, `out/`,
  `.idea/`, `*.iml`, `.vscode/`, `.kotlin/`, `local.properties`,
  Gradle wrapper jars, OS noise, Kotlin/Native intermediates, and
  test reports. Already mostly comprehensive; added `out/`,
  `local.properties`, and `.kotlin/` to plug the gaps.

## Estimated effort

~0.5 day. Mostly landed.

## Verification

```bash
./gradlew jvmTest linuxX64Test detektAll
# All green.
```
