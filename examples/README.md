# Examples

Sample apps that validate fluent-kt integrations end-to-end.

| Example | What it shows | Run |
|---|---|---|
| [`android-compose`](android-compose/) | Jetpack Compose + `fluent-compose` + codegen `remember*Messages()` | `./gradlew :examples:android-compose:installDebug` |

Open the **repo root** as the Gradle project in Android Studio (not the example folder alone).

Published consumers apply `id("io.github.ggallovalle.fluent")` with `generateComposeAccessors.set(true)`.
The in-repo sample uses a `JavaExec` bridge to `:fluent-codegen` because a sibling
project cannot be applied via `buildscript` classpath in one composite build.
