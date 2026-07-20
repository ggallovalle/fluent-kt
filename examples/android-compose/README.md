# android-compose example

Minimal Jetpack Compose app for **fluent-compose** + codegen.

## Layout

```text
src/main/assets/i18n/
  en-US/app/*.ftl
  en-US/errors/*.ftl
  es-MX/…
```

## Run

From the **repo root**:

```bash
./gradlew :examples:android-compose:installDebug
```

Or open the repo root in Android Studio and run the `android-compose` run configuration.

Tap **en-US** / **es-MX** on the home screen to switch application locales
(`AppCompatDelegate.setApplicationLocales`); strings update via
`ProvideFluentFromAssets` + `LocalConfiguration`.

## Codegen

`fluentGenerate` (JavaExec → `FluentCodegenMain`) runs before `preBuild` and
emits `rememberAppMessages()` / `rememberErrorsMessages()`. Published apps
should use `id("dev.kbroom.fluent") { generateComposeAccessors.set(true) }`
instead of the in-repo JavaExec bridge.
