# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-11-07

First public release.

### Added
- Dictation into the focused text field of any app via an accessibility service; falls back to the
  clipboard when no field is focused.
- Speech recognition on your own transcribe-text-server (OpenAI-compatible Audio API): several servers with
  selection policies and failover, per-server model, language, prompt, VAD and streaming settings,
  LLM post-processing presets.
- Launch methods, each optional: floating button, Quick Settings tile, volume key, launcher icon, widget,
  app shortcut, digital assistant / system voice input, “Share” audio and video to transcribe.
- Retry of failed uploads and dictation history.
- English and Russian UI with an in-app language setting.
- In-app updates from GitHub Releases with a daily check.
