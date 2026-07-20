# 06 — Publishing & Packaging

**Priority: MEDIUM** — Snapshot publishing is live; first non-SNAPSHOT
release and any packaging polish remain.

## Current state

Publishing is configured via `com.vanniktech.maven.publish` (root
`build.gradle.kts`) and `gradle.properties`. Central Portal account,
GitHub Actions secrets, and a successful SNAPSHOT publish are done.
See [05-ci-cd.md](05-ci-cd.md) §E for the remaining release steps.

- **Group**: `io.github.ggallovalle`
- **Default version**: `0.1.0-SNAPSHOT` (`VERSION_NAME` in
  `gradle.properties`)
- **Publishing modules**: all subprojects except `:fluent-testing`
- **Targets published today**: JVM + linuxX64

## Tasks

### A. Maven coordinates

- [x] **6.1** Maven coordinates defined:
  ```
  Group:    io.github.ggallovalle
  Artifact: fluent, fluent-syntax, fluent-bundle, fluent-fallback,
            fluent-pseudo, fluent-resmgr, intl-memoizer
  Version:  0.1.0-SNAPSHOT → (pending) 0.1.0
  ```
  Kotlin packages remain `dev.kbroom.fluent.*` (independent of Maven
  group).

### B. Gradle publishing plugins

- [x] **6.2** `com.vanniktech.maven.publish` applied to every publishing
  module from the root build (not hand-rolled `maven-publish` /
  `signing` per module).
- [x] **6.3** Shared POM metadata from `POM_*` in `gradle.properties`
  (name, description, MIT license, developer, SCM).

### C. GPG signing

- [x] **6.4** In-memory GPG signing via vanniktech
  (`signAllPublications()` + `signingInMemoryKey` /
  `signingInMemoryKeyPassword` from CI secrets).

### D. Sonatype / Maven Central

- [x] **6.5** Central Portal publishing via
  `publishToMavenCentral()` (no legacy OSSRH staging plugin).
- [x] **6.6** Credentials + namespace verified; SNAPSHOT upload
  succeeded.

### E. GitHub Packages (alternative/snapshot)

- [ ] **6.7** GitHub Packages — **not needed**. Snapshots go to Maven
  Central. Reopen only if a dual-repo setup is wanted later.

### F. KMP artifact naming

- [x] **6.8** KMP artifact names follow conventions for current
  targets (`*-jvm`, `*-linuxx64`). Additional platform suffixes land
  when those targets are added (todo/04 §4.13).

### G. Version management

- [x] **6.9** Version via `VERSION_NAME` in `gradle.properties`,
  overridable with `-PVERSION_NAME=…` or the release workflow.
- [ ] **6.10** Cut first semantic release `0.1.0` (tag `v0.1.0`). See
  [05-ci-cd.md](05-ci-cd.md) §E.6.

## Estimated effort remaining

~0.5 day for the first non-SNAPSHOT release (tag + verify on Central +
GitHub Release). Setup work in A–F is done.
