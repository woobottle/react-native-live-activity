# react-native-live-activity

> React Native library for iOS Live Activity (ActivityKit) and Android live update style notifications, behind a unified JS API.

**Status:** v0.1.0. Both iOS Live Activity (ActivityKit) and the Android ongoing notification support the full start/update/end lifecycle, and `referenceId` + `getActiveActivities` can reconnect to an activity — though only iOS actually returns still-running activities after the app process restarts; Android returns an empty array in that case. The native countdown timer (`content.timer`) currently renders **on iOS only**; the Android native module doesn't read that field at all. JS, Swift, and Kotlin each have unit tests running in CI. Real-device end-to-end verification has not been performed yet. See the **Platform behavior differences** table below for where the two platforms genuinely diverge.

---

## Why

iOS Live Activity and Android's live update / promoted ongoing notification patterns solve similar product problems — surface real-time progress (delivery, fitness, timers, transit) on the system shell — but each platform forces its own native integration work. This library aims for one small, honest JS API across both. It does not pretend the rendering models are identical. See [`PRD.md`](./PRD.md) for full product scope.

## Install

```sh
npm install @woobottle/react-native-live-activity
# or
yarn add @woobottle/react-native-live-activity
```

iOS:

```sh
cd ios && pod install
```

Android: autolinked via `react-native.config.js`.

## JS API

```ts
import {
  LiveActivity,
  type LiveActivityContent,
} from '@woobottle/react-native-live-activity'

await LiveActivity.isSupported()
// => boolean

const { activityId } = await LiveActivity.startActivity(
  {
    title: 'Pizza on the way',
    subtitle: '12 min away',
    progress: 0.4,
  },
  { referenceId: 'order-42' },
)

await LiveActivity.updateActivity(activityId, {
  title: 'Pizza on the way',
  subtitle: '4 min away',
  progress: 0.8,
})

await LiveActivity.endActivity(activityId)

// Coarse per-platform capability probe:
await LiveActivity.getPlatformCapabilities()
// iOS     => { iosLiveActivity: <areActivitiesEnabled>, androidLiveUpdate: false }
// Android => { iosLiveActivity: false, androidLiveUpdate: <notifications enabled> }

// Reconnect to an activity started before a JS process restart:
const active = await LiveActivity.getActiveActivities()
const mine = active.find(a => a.referenceId === 'order-42')
```

### Native countdown timer (iOS only)

Pass a `timer` and the **native widget draws the remaining time itself** on
iOS — no need for JS to wake up every second and call `updateActivity`. Call
it once to start, and again only on pause/resume/completion.

```ts
const now = Date.now()

// Start
await LiveActivity.startActivity({
  title: 'Focus for 10 minutes',
  timer: { startAt: now, endAt: now + 10 * 60 * 1000, state: 'running' },
})

// Pause — JS does not compute the remaining time, only the moment it paused.
await LiveActivity.updateActivity(activityId, {
  title: 'Focus for 10 minutes',
  timer: { startAt: now, endAt: now + 10 * 60 * 1000, pauseAt: Date.now(), state: 'paused' },
})

// Complete
await LiveActivity.updateActivity(activityId, {
  title: 'Focus for 10 minutes',
  timer: { startAt: now, endAt: now + 10 * 60 * 1000, state: 'completed' },
})
```

All timestamps are **epoch milliseconds**. When `state: 'paused'`, `pauseAt`
is required and must satisfy `startAt <= pauseAt <= endAt`; violating either
rejects with `E_INVALID_CONTENT`.

> **Your widget has to draw the paused state itself.**
> `Text(timerInterval:pauseTime:)` ignores `pauseTime` inside a Live Activity —
> the label keeps counting down even though ActivityKit stores `state: 'paused'`
> and `pauseAt` correctly (measured on iOS 17.4: `getActiveActivities()` reported
> a stable `pauseAt` while the Dynamic Island kept ticking). Branch on
> `timerState == "paused"` and render `endAt - pauseAt` as a plain string instead
> of a self-updating one. See `LiveActivityStatusView` in the example widget.

**This only renders on iOS.** The Android native module currently parses and
uses only `title` / `subtitle` / `progress` — `content.timer` passes JSON
validation (both platforms share the same JS types) but the Android module
never reads it, so it has **zero effect** on the Android notification. If an
Android build needs a visible countdown today, the app has to keep computing
it in JS and pushing it through `progress`/`subtitle` on its own polling
interval — the "no JS polling" benefit above is iOS-only. See **Platform
behavior differences** below.

### Types

```ts
type LiveActivityTimerState = 'running' | 'paused' | 'completed'

type LiveActivityTimer = {
  startAt: number      // epoch milliseconds
  endAt: number        // epoch milliseconds
  pauseAt?: number     // epoch milliseconds — required when state === 'paused'
  state: LiveActivityTimerState
}

type LiveActivityContent = {
  title: string
  subtitle?: string
  progress?: number    // 0..1
  timer?: LiveActivityTimer
}

type StartActivityOptions = {
  referenceId?: string
  android?: { foregroundService?: boolean }
}

type StartLiveActivityResult = { activityId: string }

type ActiveLiveActivity = {
  activityId: string
  referenceId?: string
  content: LiveActivityContent
}

type PlatformCapabilities = {
  iosLiveActivity: boolean
  androidLiveUpdate: boolean
}
```

The content schema is intentionally narrow for v1 (see PRD §3 non-goals). Richer/typed surface families may land later.

## Platform behavior differences

The two OS models genuinely diverge at these points. Documented rather than
papered over, per PRD §6 ("document platform differences honestly").

| Situation | iOS | Android |
|---|---|---|
| `updateActivity` with an unknown `activityId` | rejects with `E_NOT_FOUND` | **creates a new notification** under that id — there is no existence check before posting |
| `endActivity` with an unknown `activityId` | rejects with `E_NOT_FOUND` | resolves silently (cancelling a non-existent notification is a no-op) |
| `getActiveActivities()` after the app process restarts | ActivityKit owns activity state independently of the app process, so it **returns the still-running activities** | the snapshot lives only in an in-memory map inside the module, so it **returns an empty array**. What happens to the notification itself differs by how it was started: a plain notification (`NotificationManager.notify`) is posted independently of the process and typically survives; a **foreground-service** activity is more likely to disappear — if the OS restarts the service after the process dies, it's restarted with a `null` intent, and the service's `onStartCommand` calls `stopSelf()` immediately in that case rather than restoring anything |
| `isSupported()` / `getPlatformCapabilities()` | `ActivityAuthorizationInfo().areActivitiesEnabled` (iOS 16.1+; `false` below that) | whether `POST_NOTIFICATIONS` is granted (Android 13+) **and** the system notification toggle is on (Android 7+) — not a single permission check |
| `content.timer` (countdown rendering) | parsed, validated, and rendered live by the widget via `Text(timerInterval:)` | **not parsed at all.** The field is silently ignored; it has no effect on the notification. It is also echoed back unvalidated through `getActiveActivities()` — see note below. Rejecting bad `progress` shapes is otherwise consistent across platforms now — see note below |
| `{ android: { foregroundService: true } }` | no-op | hosted via a foreground service. v1 supports **one at a time** — starting a second foreground activity replaces the first's notification |

Note on input validation: booleans and non-finite numbers (`NaN`/`Infinity`)
for `progress` are rejected on both platforms now — iOS via an explicit
`CFGetTypeID` guard against CFBoolean-backed `NSNumber`, Android via
`ReadableType.Number` plus a `0.0..1.0` range check that also happens to
reject `NaN`. This used to be an iOS-only fix; it no longer is. (Android
doesn't need an equivalent guard for `timer` because it doesn't read that
field at all.)

Note on `getActiveActivities()` and `timer` on Android: the Android module
stores the raw JS `content` payload as-is and returns it verbatim from
`getActiveActivities()`, with no validation applied at either end. That means
a call like `startActivity({ title: 'x', timer: { startAt: 'abc', state:
'bogus' } })` succeeds on Android, and a later
`(await getActiveActivities())[0].content.timer` can come back with a
`state` outside `LiveActivityTimerState` and a `startAt` that isn't even a
`number`. Consumers must not trust `content.timer` from an Android snapshot.

Apps that need to recover after an Android restart should keep their own
activity id alongside `referenceId` (e.g. in AsyncStorage) and, on resume,
call `endActivity` followed by a fresh `startActivity` rather than relying on
`getActiveActivities()`.

## Platform notes

### iOS

- ActivityKit; minimum iOS **16.1** for Live Activity itself.
- The module loads on iOS 15.1+ so consuming apps can keep a lower deployment target; `isSupported()` returns `false` on < 16.1 or when Live Activities are disabled.
- `startActivity`, `updateActivity`, and `endActivity` use ActivityKit with the built-in `LiveActivityAttributes` / `ContentState` model.
- A Widget Extension target is required in the consuming app to render Live Activity UI. See **iOS Widget Extension setup** below. The `example/` app ships a working `LiveActivityWidget` extension you can copy from.

#### iOS Widget Extension setup

A Live Activity is *requested* from the app process (this library) but *rendered* by a
Widget Extension. ActivityKit matches the two by the `LiveActivityAttributes` type and
the shape of its `ContentState`, so the widget must use the **exact same** attributes
definition this library ships in [`ios/LiveActivityAttributes.swift`](./ios/LiveActivityAttributes.swift).

1. In Xcode: **File ▸ New ▸ Target… ▸ Widget Extension**. Name it e.g. `LiveActivityWidget`,
   check **Include Live Activity**, set the deployment target to **iOS 16.1+**.
2. Add this library's `LiveActivityAttributes.swift` to the widget target — either add the
   file to the widget's **Target Membership**, or copy it verbatim. Do **not** define a
   diverging copy, or activities will silently fail to render.
3. Implement `ActivityConfiguration(for: LiveActivityAttributes.self) { … } dynamicIsland: { … }`.
   The example's [`LiveActivityWidgetLiveActivity.swift`](./example/ios/LiveActivityWidget/LiveActivityWidgetLiveActivity.swift)
   is a complete reference (Lock Screen + Dynamic Island).
4. Add `NSSupportsLiveActivities` = `YES` to the **app** target's `Info.plist`.

The example project wires all of this up. The target was added programmatically via
[`example/ios/scripts/add_widget_target.rb`](./example/ios/scripts/add_widget_target.rb)
(runnable with the `xcodeproj` gem) if you prefer scripting over the Xcode UI.

### Android

- Live update / promoted ongoing notification path; not a 1:1 reproduction of iOS Live Activity.
- `startActivity`, `updateActivity`, and `endActivity` map to an ongoing status notification.
- Android 13+ requires `POST_NOTIFICATIONS`; this library declares the permission, but the consuming app still needs to request it before starting an activity.
- **Foreground service (opt-in):** for long-running activities that should resist being reclaimed, pass `{ android: { foregroundService: true } }` to `startActivity`:

  ```ts
  await LiveActivity.startActivity(
    { title: 'Delivery in progress', subtitle: '12 min away', progress: 0.4 },
    { android: { foregroundService: true } },
  )
  ```

  The library declares `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` (Android 14+, `dataSync` type) plus the service itself. `updateActivity` / `endActivity` are routed to the service automatically for ids started this way. v1 hosts one primary foreground activity at a time. The option is a no-op on iOS.

  **Consequence for every consumer, even those that never pass `foregroundService: true`:** `FOREGROUND_SERVICE_DATA_SYNC` is declared unconditionally in this library's manifest (`android/src/main/AndroidManifest.xml`), and Android manifest merge propagates it into the consuming app's merged manifest regardless of whether the app ever uses the option. On Android 14+ (API 34+), declaring this permission obliges a "Foreground service permissions" declaration in Play Console, and omitting that declaration at submission time can get a release rejected. If your app doesn't use `{ android: { foregroundService: true } }` and you want to avoid that obligation, remove the permission in your own app's `AndroidManifest.xml` via manifest merge:

  ```xml
  <manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission
      android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"
      tools:node="remove" />
  </manifest>
  ```

  Note the `xmlns:tools` namespace declaration above — `tools:node="remove"` is silently ignored without it.

## Project layout

```
src/                    TypeScript public API and native bridge
ios/                    Swift module + ObjC RCT_EXTERN bridge + content parser
android/                Kotlin module + ReactPackage + content parser
tests/ios/              SwiftPM package for parser unit tests (`swift test --package-path tests/ios`)
example/                Bare React Native app for manual lifecycle testing
PRD.md                  Product scope, MVP, success criteria
TECH-PLAN.md            Architecture, API shape, phased build plan
```

## Roadmap

- [x] JS API contract and type surface
- [x] iOS ActivityKit start/update/end
- [x] Android ongoing-notification start/update/end
- [x] iOS Widget Extension example (Lock Screen + Dynamic Island)
- [x] Android foreground-service opt-in
- [x] iOS native countdown timer (Android does not read `content.timer` — see the table above)
- [x] `referenceId` + `getActiveActivities` reconnect (iOS: survives a process restart; Android: resets to empty — see the table above)
- [x] JS / Swift / Kotlin unit tests + CI
- [ ] Android native countdown timer
- [ ] Real-device (iOS 16.1+ / Android 13+) E2E verification
- [ ] Push-based updates (ActivityKit push token)
- [ ] Expo config plugin
- [ ] New Architecture (TurboModule) native rewrite

## Development

Run `npm install` at the repo root **before** `npm install` inside `example/`. The root install's `prepare` hook builds `lib/` with `react-native-builder-bob`, and `example/` depends on the root package by path (`file:..`) rather than a published version, so its own install can trigger that same `prepare` hook against the root's `node_modules` — which only works once the root's build tooling is already installed.

## License

MIT
