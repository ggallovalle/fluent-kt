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
metadata), or a one-time registration outside the repo.

**The old `issues.sonatype.org` JIRA flow is gone.** Sonatype
decommissioned it on 2024-01-09 and replaced it with the Central Portal
at <https://central.sonatype.com> plus a Zendesk email support channel.
Anything you read about creating a JIRA ticket to claim a groupId is
out of date — the modern equivalent is creating an account on the
Central Portal and registering a namespace there.

### Steps for the first `0.1.0` release

1. **Decide coordinates.**
   - `group` (e.g. `dev.kbroom.fluent`).
   - `version` (start with `0.1.0`; use `-SNAPSHOT` for pre-release).
   - Decide whether each module gets its own Maven artifact or whether
     `fluent` (the umbrella) pulls everything transitively. The latter
     is the common KMP shape and matches how users will most likely
     consume the library.

2. **Generate a GPG key pair.** Maven Central requires every artifact
   to be PGP-signed.
   - Install GnuPG (`pacman -S gnupg` on Arch).
   - `gpg --gen-key` — use RSA 4096, no expiry (or rotate annually).
   - Use an email you check; that's the user id Sonatype will see.
   - `gpg --list-secret-keys --keyid-format=long` to get the key id.
   - Distribute the public key:
     `gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>`
     and also upload to `keyserver.ubuntu.com` for redundancy.

3. **Create a Central Portal account** at <https://central.sonatype.com>.
   - Sign in with Google or GitHub (or username + password).
   - The first time you sign in, you'll be prompted to verify a
     namespace. For `dev.kbroom.fluent` you'll need to prove control of
     a domain you own (a TXT record works) — DNS-based namespace
     verification, see <https://central.sonatype.org/register/namespace/>.
   - If you'd rather skip DNS, sign up with GitHub and Sonatype
     auto-grants `io.github.<your-username>` for free. Easy first
     namespace; rename later if you outgrow it.

4. **Apply `com.vanniktech.maven.publish` 0.37.0** (already declared
   `apply false` at the root of this repo). Per-module config looks
   like:
   ```kotlin
   plugins {
       id("com.vanniktech.maven.publish")
       id("com.vanniktech.maven.publish.signing")  // for in-memory GPG
   }
   mavenPublishing {
       publishToMavenCentral()
       signAllPublications()
       coordinates("dev.kbroom.fluent", "fluent-bundle", "0.1.0")
       pom {
           name.set("fluent-kt")
           description.set(project.description)
           url.set("https://github.com/kbroom/fluent-kt/")
           licenses {
               license {
                   name.set("MIT")
                   url.set("https://opensource.org/licenses/MIT")
               }
           }
           developers {
               developer {
                   id.set("kbroom")
                   name.set("Gerson")
                   url.set("https://github.com/kbroom/")
               }
           }
           scm {
               url.set("https://github.com/kbroom/fluent-kt/")
               connection.set("scm:git:git://github.com/kbroom/fluent-kt.git")
               developerConnection.set("scm:git:ssh://git@github.com/kbroom/fluent-kt.git")
           }
       }
   }
   ```
   The plugin auto-detects KMP, generates sources jars, and calls Dokka
   if applied. See <https://vanniktech.github.io/gradle-maven-publish-plugin/central/>

5. **Set up CI secrets** at <https://github.com/kbroom/fluent-kt/settings/secrets/actions>:
   - `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` — generate
     these from <https://central.sonatype.com/account> (User Token
     section — a "Username Token" pair, not your login password).
   - `GPG_PRIVATE_KEY` — `gpg --armor --export-secret-keys <KEY_ID>`,
     paste the whole block. **This is sensitive — anyone who has it
     can sign as you.**
   - `GPG_PASSPHRASE` — the passphrase you set when you generated the
     key.

6. **Add `.github/workflows/release.yml`** triggered on tag push
   matching `v*`. The flow:
   - checkout, JDK 21, Gradle cache (`gradle/actions/setup-gradle@v4`).
   - export the GPG key to a keyring the plugin can read (the
     vanniktech plugin's `signAllPublications()` reads from the env
     vars directly — no keyring needed; see plugin docs).
   - `./gradlew publishToMavenCentral`
   - create a GitHub Release from the tag with the matching
     `CHANGELOG.md` entry pasted into the release notes.

7. **Test the pipeline before your first real release.** Tag a
   `v0.1.0-SNAPSHOT` from `main`, push it, watch CI, and verify the
   artifact appears in the Central Portal staging area. **Once you
   publish from staging to Maven Central, you cannot delete or modify
   that version** — Central's long-standing policy. SNAPSHOTs are
   throwaway; use them to validate the wire-up.

8. **Tag `0.1.0` and push** once `0.1.0-SNAPSHOT` validated end-to-end.

### Common pitfalls

- **"Cannot delete/modify published component"** — Central will not
  delete artifacts. If you publish `0.1.0` with a typo, you're stuck;
  fix forward with `0.1.1`. Use SNAPSHOTs liberally during pipeline
  validation.
- **"403 Forbidden"** — usually means your namespace hasn't been
  verified yet, or your `MAVEN_CENTRAL_USERNAME`/`PASSWORD` are wrong.
  Re-check the namespace status at <https://central.sonatype.com>.
- **`gpg: signing failed: No such file or directory`** — you forgot to
  set `GPG_PASSPHRASE` in the CI environment. The vanniktech plugin
  needs both `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE`.
- **Wrong checksum files** — by default the vanniktech plugin only
  publishes `md5` and `sha1`. Central accepts these but not the others
  the gradle-publish plugin generates by default. Don't override unless
  you have a reason.

## Verification

```bash
./gradlew jvmTest linuxX64Test detektAll
# All green. The CI workflow runs the same gate on every push.
```
