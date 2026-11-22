# Changelog

This project's format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
(with an added "Known limitations" section); versioning follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.3.0] - 2026-11-22

### Added
- Android: `getActiveActivities()` now returns the activities whose plain
  notifications are still showing after the app process restarts, with their
  `referenceId` and last `content`, and `updateActivity` / `endActivity` work on
  them. Each notification carries that snapshot in its extras. Foreground-service
  activities still don't survive the process.

### Known limitations
- The Android reconnect was verified on an API 36 emulator except for one case:
  that a notification swiped away before the restart is not returned. Android 6
  (API 23) has not been checked.

## [0.2.0] - 2026-11-17

### Added
- Android: native countdown for `content.timer`. The notification's chronometer
  counts down to `endAt` without JS polling; `paused` / `completed` show fixed
  text, and an alarm redraws the notification at `00:00` when `endAt` passes,
  even with the app process dead. Exact only when the app holds an exact-alarm
  permission (not declared by the library); otherwise up to ~10 minutes late.
- Android: `content.timer` is now validated with the same rules and messages as
  iOS, so a malformed timer is rejected instead of silently accepted.

### Fixed
- iOS example widget: a paused countdown kept ticking because
  `Text(timerInterval:pauseTime:)` ignores `pauseTime` inside a Live Activity.
  The example now draws the paused remaining time as static text, and the
  README documents the constraint next to the timer contract.

### Known limitations
- Android's switch to `00:00` at `endAt` (and from fixed text to the countdown
  at a future `startAt`) is exact only when the app holds an exact-alarm
  permission; otherwise it can be minutes late, showing negative time meanwhile.
- The Android countdown was verified on an API 36 emulator; Android 6 (API 23),
  where the notification shows the end time instead of a countdown, has not been
  checked on a device or emulator.

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

[Unreleased]: https://github.com/woobottle/react-native-live-activity/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/woobottle/react-native-live-activity/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/woobottle/react-native-live-activity/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/woobottle/react-native-live-activity/releases/tag/v0.1.0
