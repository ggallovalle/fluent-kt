# 04 — Project Hygiene

**Priority: HIGH** — Missing essential files, dead code, and module structure issues
prevent publishing.

## Current problems

### A. Missing essential files

- [ ] **4.1** **README.md** — No README at all. Create one with:
  - What fluent-kt is (Kotlin Multiplatform port of Mozilla Fluent)
  - Quick start (add dependency, create bundle, format message)
  - Module overview
  - Link to Mozilla Fluent docs
  - Build instructions
  - License

- [ ] **4.2** **LICENSE** — No license file. Must decide on a license (upstream
  fluent-rs uses Apache-2.0). Add `LICENSE` file.

- [ ] **4.3** **CHANGELOG.md** — Start tracking changes. Initial entry for the
  current state.

- [ ] **4.4** **CONTRIBUTING.md** — Contribution guidelines, code style, detekt
  requirements, TDD expectations.

### B. Dead directories to remove

These directories exist on disk but are NOT in `settings.gradle.kts` and contain
no source files:

- [ ] **4.5** `fluent-compiler/` — empty shell, never implemented
- [ ] **4.6** `fluent-compiler-annotations/` — empty shell
- [ ] **4.7** `fluent-gradle-plugin/` — empty shell
- [ ] **4.8** `fluent-sample/` — empty shell (has `build.gradle.kts` only)

```bash
rm -rf fluent-compiler fluent-compiler-annotations fluent-gradle-plugin fluent-sample
```

### C. Module description in build.gradle.kts

- [ ] **4.9** Each module's `build.gradle.kts` should have a `description` field
  for Maven POM generation:
  ```kotlin
  description = "Fluent syntax parser and serializer for Kotlin Multiplatform"
  ```

### D. Gradle wrapper and dependencies

- [ ] **4.10** Verify Gradle wrapper version is current (check `gradle/wrapper/gradle-wrapper.properties`)
- [ ] **4.11** Verify dependency versions in `gradle/libs.versions.toml` are current
- [ ] **4.12** Check for unused dependencies in each module

### E. Kotlin targets

- [ ] **4.13** Current targets: JVM + LinuxX64. For v1, consider adding:
  - `macosArm64` / `macosX64` (required for Apple users)
  - `iosArm64` / `iosX64` / `iosSimulatorArm64` (mobile)
  - `js` / `wasmJs` (web)
  - Verify each target compiles and tests pass

### F. .gitignore cleanup

- [ ] **4.14** Verify `.gitignore` covers:
  - `build/`, `.gradle/`, `.idea/`
  - `*.iml`, `local.properties`
  - `out/`

## Estimated effort

~0.5 day for A-D; ~1 day for E (KMP target expansion); trivial for F.
