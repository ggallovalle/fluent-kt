# 06 — Publishing & Packaging

**Priority: MEDIUM** — Library cannot be consumed by other projects until published.

## Current state

No publishing configuration. The project is a local build only.

## Tasks

### A. Maven coordinates

- [ ] **6.1** Define Maven coordinates:
  ```
  Group:    dev.kbroom.fluent
  Artifact: fluent-syntax, fluent-bundle, fluent-fallback, etc.
  Version:  0.1.0-SNAPSHOT → 1.0.0
  ```

### B. Gradle publishing plugins

- [ ] **6.2** Add `maven-publish` plugin to each module's `build.gradle.kts`:
  ```kotlin
  plugins {
      kotlin("multiplatform")
      `maven-publish`
      signing
  }
  ```

- [ ] **6.3** Configure POM metadata:
  ```kotlin
  publishing {
      publications {
          withType<MavenPublication> {
              pom {
                  name.set("fluent-bundle")
                  description.set("Fluent message resolution for Kotlin Multiplatform")
                  url.set("https://github.com/kbroom/fluent-kt")
                  licenses { /* Apache-2.0 */ }
                  developers { /* ... */ }
              }
          }
      }
  }
  ```

### C. GPG signing

- [ ] **6.4** Configure signing (required for Maven Central):
  ```kotlin
  signing {
      // Uses gradle.properties: signing.keyId, signing.password, signing.secretKeyRingFile
      sign(publishing.publications)
  }
  ```

### D. Sonatype/Maven Central

- [ ] **6.5** Add Sonatype OSSRH plugin or use `io.github.gradle-nexus.publish-plugin`
- [ ] **6.6** Configure repository URLs:
  ```kotlin
  repositories {
      maven {
          name = "sonatype"
          url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
          credentials { /* from gradle.properties or env vars */ }
      }
  }
  ```

### E. GitHub Packages (alternative/snapshot)

- [ ] **6.7** Configure GitHub Packages for SNAPSHOT versions:
  ```kotlin
  repositories {
      maven {
          name = "github"
          url = uri("https://maven.pkg.github.com/kbroom/fluent-kt")
          credentials {
              username = System.getenv("GITHUB_ACTOR")
              password = System.getenv("GITHUB_TOKEN")
          }
      }
  }
  ```

### F. KMP artifact naming

- [ ] **6.8** Verify KMP artifact names follow conventions:
  - `fluent-syntax-jvm`
  - `fluent-syntax-linuxx64`
  - `fluent-syntax-macosarm64`
  - etc.

### G. Version management

- [ ] **6.9** Use a version plugin (e.g., `com.palantir.git-version`) or manual
  `version` in `gradle.properties`
- [ ] **6.10** Consider semantic versioning: `0.1.0` for initial release

## Estimated effort

~1 day for A-F; ~0.5 day for G (versioning strategy).
