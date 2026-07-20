# 05 — CI/CD Pipeline

**Priority: HIGH** — Tracks the verification + publishing pipeline.
The verification gate runs on every push and PR via GitHub Actions.
Snapshot publishing to Maven Central is live; the first non-SNAPSHOT
release is still outstanding.

## Status

- **CI verification workflow**: live (`.github/workflows/ci.yml`).
  Runs `detektAll`, `jvmTest`, `linuxX64Test` on every push to `main` and
  on every PR.
- **Release workflow**: live (`.github/workflows/release.yml`).
  Triggers on tag push (`v*`) and supports manual dispatch. Uses the
  vanniktech maven-publish plugin (declared at the root, applied to all
  publishing modules) which reads `MAVEN_CENTRAL_USERNAME`,
  `MAVEN_CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE` from
  GitHub Actions secrets.
- **Dependabot**: configured (`.github/dependabot.yml`). Weekly digest
  for Gradle deps (grouped minor+patch into a single PR) and GitHub
  Actions versions.
- **Group**: `io.github.ggallovalle` (Sonatype namespace granted via
  GitHub login; Central Portal account is set up and verified).
- **Maven Central credentials**: GitHub Actions secrets
  (`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`,
  `GPG_PASSPHRASE`) are configured and working.
- **Snapshot publishing**: ✅ live. `VERSION_NAME=0.1.0-SNAPSHOT` in
  `gradle.properties` by default; override via `-PVERSION_NAME=…` or
  the workflow's `version` input. A SNAPSHOT has been published
  successfully to Central.

## What works today

| Check | Status |
|---|---|
| Detekt on every push / PR | ✅ (`.github/workflows/ci.yml`) |
| JVM tests on every push / PR | ✅ |
| LinuxX64 (Kotlin/Native) tests on every push / PR | ✅ |
| Cached Gradle daemon / build cache | ✅ (via `gradle/actions/setup-gradle@v4`) |
| Dependabot weekly digest | ✅ (`.github/dependabot.yml`) |
| macOS / iOS / wasm CI matrix | ❌ — targets not in module config yet (todo/04 §4.13). Reopen this todo when targets are added. |
| Snapshot publishing | ✅ |
| Release publishing to Maven Central | ❌ — first non-SNAPSHOT (`0.1.0`) not cut yet |

## Task status

### A. GitHub Actions

- [x] **5.1** `.github/workflows/ci.yml` runs the full verification gate on
  every push and PR. Ubuntu only, JDK 21 (matches `detektJvmTarget`).
  Caching handled by `gradle/actions/setup-gradle@v4` — no separate
  `actions/cache` step.
- [x] **5.2** Cache Gradle dependencies: handled by setup-gradle action
  via its built-in `gradle-user-home` cache. No manual `actions/cache`
  step.
- [x] **5.3** Cache Kotlin/Native compiler downloads: handled implicitly
  by Gradle's dependency cache (klibs land under `~/.gradle/caches`).
  No additional config needed.
- [x] **5.4** Snapshot publishing: done. A SNAPSHOT has been published
  to Maven Central. Set `VERSION_NAME` via `-PVERSION_NAME=…` or run
  the release workflow with the `version` input. The vanniktech plugin
  handles in-memory GPG signing from env vars (no keyring import step
  needed).
- [x] **5.5** Release publishing: pipeline ready; first non-SNAPSHOT
  not cut yet. Tag `v0.1.0` (or any `v*`) on the commit you want to
  release; the workflow publishes the matching artifact to Maven
  Central and creates a GitHub Release. CI secrets
  (`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`,
  `GPG_PASSPHRASE`) are set at
  <https://github.com/ggallovalle/fluent-kt/settings/secrets/actions>
  and validated by the successful SNAPSHOT publish. The workflow maps
  these to the vanniktech plugin's required names
  (`ORG_GRADLE_PROJECT_mavenCentralUsername` etc.).

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

## Section E: First-publish checklist

One-time setup and SNAPSHOT validation are done. Remaining work is
cutting the first non-SNAPSHOT release.

- [x] **E.1** Central Portal account at <https://central.sonatype.com>
  (GitHub login as `ggallovalle`). Namespace `io.github.ggallovalle`
  verified.
- [x] **E.2** User Token generated for publishing.
- [x] **E.3** GPG private key exported (armored) for in-memory CI signing.
- [x] **E.4** Four GitHub Actions secrets set and working:
  `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`,
  `GPG_PASSPHRASE`.
- [x] **E.5** SNAPSHOT publish succeeded (pipeline + credentials validated).
- [ ] **E.6** Tag the real `0.1.0` release:
  ```bash
  git tag v0.1.0
  git push origin v0.1.0
  ```
  Watch CI at <https://github.com/ggallovalle/fluent-kt/actions>.
  After Central validates the artifact, the workflow creates a GitHub
  Release at the tag with auto-generated notes.
- [ ] **E.7** Remember: once published, the version is immutable.
  Central will not delete or modify artifacts. If `0.1.0` ships with a
  typo, fix forward with `0.1.1`. SNAPSHOTs remain the safety net.

### What the published artifacts look like

For each release, seven Maven artifacts publish per KMP variant:

```
io.github.ggallovalle:fluent:0.1.0          (umbrella metadata)
io.github.ggallovalle:fluent-jvm:0.1.0     (umbrella, JVM)
io.github.ggallovalle:fluent-linuxx64:0.1.0 (umbrella, LinuxX64 Native)
io.github.ggallovalle:fluent-bundle:0.1.0 (module metadata)
io.github.ggallovalle:fluent-bundle-jvm:0.1.0
io.github.ggallovalle:fluent-bundle-linuxx64:0.1.0
... (fluent-pseudo, fluent-fallback, fluent-resmgr, fluent-syntax,
     intl-memoizer — same triple each)
```

Users depending on `io.github.ggallovalle:fluent-jvm:0.1.0` get the
umbrella POM that lists `fluent-bundle-jvm`, `fluent-pseudo-jvm`, etc.
as transitive deps. Gradle module metadata routes consumers to the
right variant for their target.

### Common pitfalls

- **"403 Forbidden"** at publish — `MAVEN_CENTRAL_USERNAME`/`PASSWORD`
  wrong, or namespace not yet verified. Re-check
  <https://central.sonatype.com>.
- **`gpg: signing failed`** — `GPG_PASSPHRASE` missing from CI env
  vars. The vanniktech plugin needs both `GPG_PRIVATE_KEY` and
  `GPG_PASSPHRASE` set.
- **Tag push didn't trigger release** — make sure the tag matches
  `v*` exactly (e.g. `v0.1.0`, not `0.1.0` or `release-0.1.0`). The
  workflow's `tags: ['v*']` filter rejects other shapes.
- **Version already exists on Central** — happens if you re-push a
  tag without bumping. Maven Central rejects duplicate versions.
  Delete the tag, bump version, retry. **Never `git push --force`
  tags.**

## Verification

```bash
./gradlew jvmTest linuxX64Test detektAll
# All green. The CI workflow runs the same gate on every push.
```
