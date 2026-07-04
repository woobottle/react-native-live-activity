# react-native-live-activity

> React Native library for iOS Live Activity (ActivityKit) and Android live update style notifications, behind a unified JS API.

**Status:** native lifecycle + iOS Widget Extension in place. The JS API surface, iOS ActivityKit lifecycle calls, the iOS Live Activity UI (Lock Screen + Dynamic Island) in the example app, and Android ongoing notification lifecycle calls are all implemented. Remaining work is an end-to-end build/device verification pass (blocked locally by an out-of-date Xcode toolchain). See [`TECH-PLAN.md`](./TECH-PLAN.md) for the phased build path.

---

## Why

iOS Live Activity and Android's live update / promoted ongoing notification patterns solve similar product problems — surface real-time progress (delivery, fitness, timers, transit) on the system shell — but each platform forces its own native integration work. This library aims for one small, honest JS API across both. It does not pretend the rendering models are identical. See [`PRD.md`](./PRD.md) for full product scope.

## Install

```sh
npm install react-native-live-activity
# or
yarn add react-native-live-activity
```

iOS:

```sh
cd ios && pod install
```

Android: autolinked via `react-native.config.js`.

## JS API

```ts
import { LiveActivity, type LiveActivityContent } from 'react-native-live-activity'

await LiveActivity.isSupported()
// => boolean

const { activityId } = await LiveActivity.startActivity({
  title: 'Pizza on the way',
  subtitle: '12 min away',
  progress: 0.4,
})

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
```

### Types

```ts
type LiveActivityContent = {
  title: string
  subtitle?: string
  progress?: number
}

type StartLiveActivityResult = {
  activityId: string
}

type PlatformCapabilities = {
  iosLiveActivity: boolean
  androidLiveUpdate: boolean
}
```

The content schema is intentionally narrow for v1 (see PRD §3 non-goals). Richer/typed surface families may land later.

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
- `startActivity`, `updateActivity`, and `endActivity` currently map to an ongoing status notification.
- Android 13+ requires `POST_NOTIFICATIONS`; this library declares the permission, but the consuming app still needs to request it before starting an activity.
- A foreground service may still be needed for long-running production use cases (TECH-PLAN §4).

## Project layout

```
src/                    TypeScript public API and native bridge
ios/                    Swift module + ObjC RCT_EXTERN bridge
android/                Kotlin module + ReactPackage
example/                Bare React Native app for manual lifecycle testing
PRD.md                  Product scope, MVP, success criteria
TECH-PLAN.md            Architecture, API shape, phased build plan
```

## Roadmap

Phases tracked in [`TECH-PLAN.md`](./TECH-PLAN.md):

1. ✅ Repo scaffold + docs
2. ✅ JS API contract and type surface
3. ✅ iOS native module stub
4. ✅ Android native module stub
5. ✅ Example app baseline
6. ✅ iOS `startActivity` working path
7. ✅ Android `startActivity` working path
8. ✅ iOS `update` / `end` working path
9. ✅ Android `update` / `end` working path
10. ⏳ Docs hardening

## License

MIT
