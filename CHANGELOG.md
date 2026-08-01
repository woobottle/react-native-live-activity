# Changelog

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and [Semantic Versioning](https://semver.org/).

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
- `content.timer` is parsed and validated on both platforms, but only the iOS
  widget renders a live countdown from it — Android's native module does not
  read the field at all, so it has no effect on the Android notification.
- `getActiveActivities()` only returns still-running activities after a
  process restart on iOS; Android always returns an empty array in that case
  because its snapshot lives only in process memory.
- No real-device (iOS 16.1+ / Android 13+) end-to-end verification has been
  recorded yet for this release.

### Fixed
- iOS: JS booleans and `NaN` used to pass `progress` / timer boundary validation
- Android: resolving the consumer's Kotlin version could make Gradle throw and fail the build

[Unreleased]: https://github.com/woobottle/react-native-live-activity/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/woobottle/react-native-live-activity/releases/tag/v0.1.0
