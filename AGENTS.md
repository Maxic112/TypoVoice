# TypoVoice

Android dictation app: records speech, sends it to a self-hosted transcribe-text-server instance
(OpenAI-compatible Audio API plus extensions under `extv1`, base URL `https://<host>/api/transcribe/v1`)
and inserts the recognized text into the focused field of any app. minSdk 31, targetSdk 37.

## Build & run
- `source scripts/env.sh` — puts JDK 21 and the Android SDK on PATH (`JAVA_HOME` / `ANDROID_HOME` from the
  environment win; Homebrew paths are the fallback).
- `scripts/test.sh` — JVM unit tests; `scripts/install.sh` — build, install and launch over adb;
  `scripts/logs.sh` — logcat of the app.
- Local config lives in `local.properties` (git-ignored, see `local.properties.example`). An optional dev
  server from it is seeded into debug builds on first launch; build with `-Ptypovoice.cleanStart` for a debug
  APK without it (first-run testing). Release builds never contain these values.
- Gradle build cache, configuration cache and file-system watching (`org.gradle.vfs.watch`) are disabled on
  purpose: with AGP 9.4 they produced stale APKs (old manifest/resources). If a change seems ignored,
  `rm -rf app/build` and rebuild.
- Android 17 requires the runtime permission `ACCESS_LOCAL_NETWORK` for servers on the local network.

## Release
- CI: `.github/workflows/build.yml` (tests, lint, debug APK on push/PR); `release.yml` runs on a `vX.Y.Z` tag:
  builds a signed APK with versionName from the tag (versionCode = major·10000 + minor·100 + patch),
  publishes a GitHub Release `TypoVoice-X.Y.Z.apk` with notes from the matching CHANGELOG section.
- Signing: `SIGNING_*` environment variables (repository secrets in CI) or the git-ignored
  `keystore.properties` (see `keystore.properties.example`); without them the release APK is unsigned.
- To release: move the CHANGELOG "Unreleased" entry to the version with a date, commit, tag `vX.Y.Z`, push the tag.

## Architecture
- `core/dictation/DictationController` — single owner of record → upload → deliver; entry points
  only call `start/stop/toggle/cancel`. `DictationService` is the microphone foreground service,
  alive only during a dictation.
- `core/api/SttApiClient` — API client (transcriptions incl. SSE stream, `/models`, `/extv1/presets`,
  `/extv1/health`) + typed errors `SttApiException`; `core/servers/ServerPool` — per-server settings
  (`ServerConfig`: model, language, initial prompt, VAD, stream, LLM preset, own prompt with `{text}`),
  selection policies and failover. `core/postprocess/Postprocess` — quick preset choice over server settings.
- `a11y/TypoVoiceAccessibilityService` — focus tracking, hosts the floating bubble
  (`TYPE_ACCESSIBILITY_OVERLAY`), inserts text. `overlay/` — bubble UI and gestures.
- `tile/` — optional Quick Settings tile.
- `core/update/UpdateManager` — in-app updates from GitHub Releases of `typovoice.updateRepo`
  (gradle.properties; empty = not configured): daily WorkManager check, APK download, install via PackageInstaller.
- Text: every UI string lives in `res/values/strings.xml` (English, default) and `res/values-ru/`;
  code calls `str(R.string.x, args)` / `plural(R.plurals.x, n)` from `Strings.kt` — never hard-code UI text.
  App language: system by default, overridable in Settings → Language (`AppLanguage`, per-app locales).
- `ui/` — Compose UI: `AppRoot` (Navigation Compose, no bottom bar: home is the root, settings via the
  top-bar gear, history via the home card), `HomeScreen`, `HistoryScreen`, `settings/*` (one file per
  settings section), shared rows in `Components.kt`.

## Product decisions
- No custom keyboard (IME) and no permanent notification.
- Every entry point is optional and toggleable in settings.
- Distributed as an APK via GitHub Releases (sideload); Google Play is not a goal for now.

## Languages
- Code comments and doc-comments: English. Comment only non-obvious logic, decisions and workarounds.
- Commit messages: English.
- Documentation (README.md, docs/, AGENTS.md): English.
- Russian copies exist only for user-facing docs: README.ru.md and `*.ru.md` guides. When a user-facing doc
  changes, update its Russian copy in the same commit. Development docs are English only.
- Screenshots: `docs/images/<name>.en.png` for English docs, `<name>.ru.png` for Russian; only images used
  by a README live there.
- UI strings are localized resources; add every new string in both languages.

## Public repository hygiene
- Never commit secrets, personal data, hostnames, IP addresses, local paths or test-device details.
  Local environment lives in AGENTS.local.md (git-ignored); plans and notes in `docs/local/` (git-ignored).
- Docs describe the current state of the project only.

## Commits
- Author identity is configured per repository; never change the global git config.
- Message: imperative English summary ≤ 72 chars, optional body explaining why.
- No private data, device names or hosts in messages.
