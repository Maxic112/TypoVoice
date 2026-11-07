<p align="center">
  <img src="docs/images/icon.png" width="112" alt="TypoVoice icon">
</p>
<h1 align="center">TypoVoice</h1>
<p align="center">Voice typing into any app on Android, recognized by your own speech-to-text server.</p>

<p align="center">
  <a href="README.md"><img alt="English" src="https://img.shields.io/badge/🇬🇧_English-selected-0969DA?style=for-the-badge"></a>
  <a href="README.ru.md"><img alt="Русский" src="https://img.shields.io/badge/🇷🇺_%D0%A0%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9-gray?style=for-the-badge"></a>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-12%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="API" src="https://img.shields.io/badge/API-31%2B-brightgreen?style=flat-square">
  <a href="https://github.com/Maxic112/TypoVoice/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/Maxic112/TypoVoice?style=flat-square"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-MIT-blue?style=flat-square"></a>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white">
</p>

## About

TypoVoice records your speech, sends it to a self-hosted
transcribe-text-server (OpenAI-compatible Audio API) and types the recognized text into the text field that
is focused in any app — messengers, notes, browsers. It is not a keyboard: you keep your usual keyboard and
start dictation with a floating button, a tile, a volume key or the system voice input.

<!-- Screenshots: add docs/images/{home,settings,launch}.en.png and uncomment.
<p align="center">
  <img src="docs/images/home.en.png" width="230" alt="Home screen">
  <img src="docs/images/launch.en.png" width="230" alt="Launch methods">
  <img src="docs/images/settings.en.png" width="230" alt="Settings">
</p>
-->

## Features

- 🎙️ **Dictate anywhere** — the text goes straight into the focused field; with no field it lands in the clipboard.
- 🖥️ **Your own server** — works with transcribe-text-server: choose the model, language, prompt, VAD and streaming per server.
- 🔁 **Several servers** — selection policies and automatic failover; failed uploads can be retried.
- ✨ **Post-processing** — LLM presets on the server (punctuation, cleanup, your own prompt with `{text}`).
- 🚀 **Launch your way** — floating button, Quick Settings tile, volume key, launcher icon, widget, app shortcut, digital assistant and system voice input, “Share” audio or video to transcribe. Each one can be turned off.
- 🕘 **History** of recent dictations.
- 🌐 **English and Russian** interface, following the system language or set in the app.
- ⬆️ **Updates from GitHub Releases** — checked daily, installed from the app.

## Install

1. Download the latest `TypoVoice-*.apk` from [Releases](https://github.com/Maxic112/TypoVoice/releases/latest).
2. Open it on the phone and allow installing apps from this source when Android asks.
3. In TypoVoice add your server (base URL like `https://stt.example.com/api/transcribe/v1` and an API key)
   and turn on the accessibility service when the app asks.

Requires Android 12 or newer and a running transcribe-text-server instance.

### Permissions

| Permission | Why |
|---|---|
| Microphone | Records audio only while dictation is running (foreground service with a notification). |
| Accessibility service | Finds the focused text field, inserts the text and shows the floating button. Screen content is not stored or sent anywhere. |
| Notifications | Shows the recording notification and errors. |
| Local network | Reaches a server on your local network (Android 17+). |
| Install apps | Installs updates downloaded from GitHub Releases. |

Audio is sent only to the servers you configure. Nothing else leaves the device.

## Build from source

Requirements: JDK 21, Android SDK.

```bash
git clone https://github.com/Maxic112/TypoVoice.git
cd TypoVoice
cp local.properties.example local.properties   # set sdk.dir; the dev server is optional
./gradlew :app:assembleDebug                   # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest
```

`local.properties` may hold a dev server (`typovoice.dev.*`) that debug builds add on first launch.
Release builds never contain it.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security issues: [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE) © 2026 Maxim Veretelnikov
