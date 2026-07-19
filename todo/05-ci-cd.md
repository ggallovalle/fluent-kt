# 05 — CI/CD Pipeline

**Priority: HIGH** — Tracks the verification + publishing pipeline.
The verification gate runs on every push and PR via GitHub Actions;
publishing is deferred until we're ready to cut a release.

## Status

- **CI verification workflow**: live (`.github/workflows/ci.yml`).
  Runs `detektAll`, `jvmTest`, `linuxX64Test` on every push to `main` and
  on every PR.
- **Dependabot**: configured (`.github/dependabot.yml`). Weekly digest
  for Gradle deps (grouped minor+patch into a single PR) and GitHub
  Actions versions.
- **Snapshot/release publishing**: deferred. The `maven-publish`
  plugin is declared in the root `build.gradle.kts` (`apply false`) but
  no module actually publishes, and no group/version/signing config
  exists. See **Section E: Publishing checklist** below for the steps
  to do this for the first time.

## What works today

| Check | Status |
|---|---|
| Detekt on every push / PR | ✅ (`.github/workflows/ci.yml`) |
| JVM tests on every push / PR | ✅ |
| LinuxX64 (Kotlin/Native) tests on every push / PR | ✅ |
| Cached Gradle daemon / build cache | ✅ (via `gradle/actions/setup-gradle@v4`) |
| Dependabot weekly digest | ✅ (`.github/dependabot.yml`) |
| macOS / iOS / wasm CI matrix | ❌ — targets not in module config yet (todo/04 §4.13). Reopen this todo when targets are added. |
| Snapshot publishing | ❌ |
| Release publishing to Maven Central | ❌ |

## Task status

### A. GitHub Actions

- [x] **5.1** `.github/workflows/ci.yml` runs the full verification gate on
  every push and PR. Ubuntu only, JDK 21 (matches `detektJvmTarget`).
  Caching handled by `gradle/actions/setup-gradle@v4` — no separate
  `actions/cache` step needed.
- [x] **5.2** Cache Gradle dependencies: handled by setup-gradle action
  via its built-in `gradle-user-home` cache. No manual `actions/cache`
  step.
- [x] **5.3** Cache Kotlin/Native compiler downloads: handled implicitly
  by Gradle's dependency cache (klibs land under `~/.gradle/caches`).
  No additional config needed.
- [ ] **5.4** Snapshot publishing: deferred. See Section E.
- [ ] **5.5** Release publishing: deferred. See Section E.

### B. KMP target matrix

- [ ] **5.6** Multi-OS CI matrix: deferred. Adding `macosArm64Test`,
  `iosSimulatorArm64Test`, etc. requires the corresponding Kotlin
  targets to be enabled in each module's `kotlin {}` block plus
  matching `PlatformIntl.actual` implementations. Currently the
  supported targets are JVM + LinuxX64 only (see todo/04 §4.13). When
  4.13 lands, expand the CI matrix to:
  - ubuntu: jvmTest, linuxX64Test
  - macos: macosArm64Test, iosSimulatorArm64Test
  - windows: mingwX64Test (if/when that target is added)

### C. Code quality gates

- [x] **5.7** `detektAll` runs in CI and is required to pass before merge.
- [x] **5.8** ktlint runs as part of the ktlint-wrapper plugin bundled
  with detekt. Same gate as 5.7.
- [ ] **5.9** Test coverage threshold (JaCoCo): not configured. Optional.
  Add when there's a target coverage percentage worth enforcing.
- [x] **5.10** Dependency vulnerability scanning via Dependabot weekly
  digest. Dependabot opens PRs for Gradle deps and GitHub Actions
  versions.

## Section E: Publishing checklist

This is for the maintainer (KBroom) to do manually the first time. I
have not configured any of this because each step requires either a
secret you hold, a decision only you can make (group id, version, license
metadata), or a one-time interaction with Sonatype/Maven Central.

When you're ready to publish `0.1.0`:

1. **Decide coordinates.**
   - `group` (e.g. `dev.kbroom.fluent`).
   - `version` (start with `0.1.0`; use `-SNAPSHOT` for pre-release).
   - Whether each module gets its own artifact or whether `fluent`
     pulls everything transitively. The latter is the common KMP shape.

2. **Wire `maven-publish` per module.**
   - In each `build.gradle.kts` that should publish, replace
     `id("dev.detekt")` with `id("maven-publish")` plus a `publishing {}`
     block: `group`, `version`, `pom { name, description, url, licenses,
     developers, scm }`. Reuse the module's existing `description`
     field (added in todo/04 §4.9) for `pom.description`.
   - The `com.vanniktech.maven.publish` plugin (declared `apply false`
     at the root) can replace the bare `maven-publish` plugin if you
     want Central + Gradle Plugin Portal publishing in one config. See
     https://github.com/vanniktech/gradle-maven-publish-plugin.

3. **Sign releases.** Add the `signing` plugin and a `signing { ... }`
   block reading keys from environment variables. Without this,
   Maven Central rejects the upload.

4. **Register a Sonatype account** at https://issues.sonatype.org (or
   use the new Central Portal at https://central.sonatype.com) and
   create a `dev.kbroom.fluent` namespace ticket. Until they approve
   your group id, every publish attempt will fail with `403 Forbidden`.

5. **Add CI secrets** for whichever publisher you choose:
   - Maven Central: `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD`
     (or a Sonatype user token), plus `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE`
     if you sign in CI.
   - GitHub Packages: `GITHUB_TOKEN` (built-in) plus a Maven `repository`
     block pointing at `maven.pkg.github.com/<owner>/<repo>`.

6. **Add `.github/workflows/release.yml`** (see the workflow skeleton in
   the git history of fluent-rs or any other project using `vanniktech.maven.publish`).
   Trigger on tag push matching `v*`. Run `./gradlew publish` with the
   credentials above, then create a GitHub Release from the tag with
   `CHANGELOG.md` excerpted for that version.

7. **First release test.** Cut a `0.1.0-SNAPSHOT` from `main` before
   `0.1.0` to validate the pipeline end-to-end. Verify the artifact
   appears in the snapshot repo.

## Verification

```bash
./gradlew jvmTest linuxX64Test detektAll
# All green. The CI workflow runs the same gate on every push.
```
