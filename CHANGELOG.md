# Changelog

This project's format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
(with an added "Known limitations" section); versioning follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] - 2026-08-01

First public release.

### Added
- Unified JS API: `isSupported`, `getPlatformCapabilities`, `getActiveActivities`,
  `startActivity`, `updateActivity`, `endActivity`
- iOS: ActivityKit-based Live Activity lifecycle (runtime-gated to iOS 16.1+, module loads on 15.1+)
- iOS: Lock Screen + Dynamic Island widget example with a shared `LiveActivityAttributes`
- iOS: native countdown timer (`content.timer`) rendered live by the widget via `Text(timerInterval:)`
- Android: ongoing (always-on) notification lifecycle, notification channel management
- Android: opt-in foreground service (`{ android: { foregroundService: true } }`)
- `referenceId` + `getActiveActivities` to reconnect after a JS process restart (iOS only — see README **Platform behavior differences**)
- Swift / Kotlin content-parser unit tests, GitHub Actions CI

### Known limitations
- `content.timer` is parsed and validated on **iOS only**. On Android the
  field is not read at all — not parsed, not validated, just ignored — so a
  malformed `timer` payload is silently accepted there instead of being
  rejected, and no countdown is rendered either way.
- `getActiveActivities()` only returns still-running activities after a
  process restart on iOS; Android always returns an empty array in that case
  because its snapshot lives only in process memory.
- No real-device (iOS 16.1+ / Android 13+) end-to-end verification has been
  recorded yet for this release.
- Android's opt-in foreground service (`{ android: { foregroundService: true } }`)
  hosts only **one** primary foreground activity at a time; starting a second
  foreground activity replaces the first's notification.

### Fixed
- iOS: JS booleans and `NaN` used to pass `progress` / timer boundary validation
- Android: resolving the consumer's Kotlin version could make Gradle throw and fail the build

[Unreleased]: https://github.com/woobottle/react-native-live-activity/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/woobottle/react-native-live-activity/releases/tag/v0.1.0
