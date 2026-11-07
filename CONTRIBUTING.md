# Contributing to TypoVoice

Thanks for your interest! Bug reports, ideas and pull requests are welcome.

## Before you start
- For anything larger than a small fix, open an issue first so we can agree on the approach.
- Keep pull requests focused: one change per PR.

## Build and test
Requirements: JDK 21 and the Android SDK (`ANDROID_HOME`).

```bash
cp local.properties.example local.properties   # set sdk.dir; the dev server is optional
./gradlew :app:assembleDebug                   # build the debug APK
./gradlew :app:testDebugUnitTest               # JVM unit tests
./gradlew :app:lintDebug                       # Android lint
```

Helper scripts (macOS/Linux): `source scripts/env.sh`, then `scripts/test.sh`, `scripts/install.sh`
(build, install and launch on a connected device), `scripts/logs.sh` (logcat of the app).

## Code style
- Kotlin official style (`kotlin.code.style=official`), 4-space indent; see `.editorconfig`.
- UI text never goes into code: add strings to `res/values/strings.xml` (English) **and**
  `res/values-ru/strings.xml` (Russian), use `str(R.string.x)` / `plural(R.plurals.x, n)`.
- Code comments are in English and explain only non-obvious logic, decisions and workarounds.
- `AGENTS.md` describes the architecture; update it when you change the structure.

## Commits and pull requests
- Commit messages: English, imperative summary ≤ 72 characters, optional body explaining why.
- Make sure the unit tests and lint pass and the docs are updated.
- Never commit secrets, server addresses, API keys or personal data. Local configuration belongs in
  `local.properties`, which is git-ignored.

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
