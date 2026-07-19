# 05 — CI/CD Pipeline

**Priority: HIGH** — No CI/CD exists. Every commit should verify: lint, tests on all
targets, and optionally publish snapshots.

## Current state

No `.github/`, `.gitlab-ci.yml`, or Jenkinsfile. Builds are local-only.

## Tasks

### A. GitHub Actions (recommended)

- [ ] **5.1** Create `.github/workflows/ci.yml`:
  ```yaml
  on: [push, pull_request]
  jobs:
    build:
      strategy:
        matrix:
          os: [ubuntu-latest, macos-latest]
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-java@v4
          with: { distribution: temurin, java-version: 17 }
        - run: ./gradlew detektAll
        - run: ./gradlew jvmTest linuxX64Test  # ubuntu
        - run: ./gradlew jvmTest macosArm64Test  # macos
  ```

- [ ] **5.2** Cache Gradle dependencies (`actions/cache` for `~/.gradle/caches`)

- [ ] **5.3** Cache Kotlin/Native compiler downloads

### B. Snapshot publishing

- [ ] **5.4** `.github/workflows/snapshot.yml`:
  - On `main` push: build + publish SNAPSHOT to GitHub Packages or Maven Central
  - Version: `0.1.0-SNAPSHOT` + git short SHA

### C. Release publishing

- [ ] **5.5** `.github/workflows/release.yml`:
  - On tag push (`v*`): build + publish to Maven Central
  - Use `signing` plugin for GPG signatures
  - Use `maven-publish` plugin for publication
  - Create GitHub Release with changelog

### D. KMP target matrix

- [ ] **5.6** CI matrix should cover all supported targets:
  | OS | Targets |
  |----|---------|
  | ubuntu-latest | jvmTest, linuxX64Test |
  | macos-latest | macosArm64Test, iosSimulatorArm64Test |
  | windows-latest | mingwX64Test (if needed) |

### E. Code quality gates

- [ ] **5.7** detektAll must pass (already configured)
- [ ] **5.8** ktlint must pass (already configured via detekt)
- [ ] **5.9** Test coverage threshold (optional: add JaCoCo for JVM tests)
- [ ] **5.10** Dependency vulnerability scanning (Dependabot or Renovate)

## Estimated effort

~0.5 day for A; ~1 day for B-D (publishing config); ~0.5 day for E.
