# react-native-live-activity

> React Native library for iOS Live Activity (ActivityKit) and Android live update style notifications, behind a unified JS API.

**Status:** early scaffold. The JS API surface and native module bridges are in place; native start/update/end implementations are intentionally stubbed and return `E_NOT_IMPLEMENTED`. See [`TECH-PLAN.md`](./TECH-PLAN.md) for the phased build path.

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
- The module loads on iOS 15.1+ so consuming apps can keep a lower deployment target; `isSupported()` returns `false` on < 16.1.
- A Widget Extension target is required in the consuming app to render Live Activity UI. The extension setup story is part of upcoming phases — see TECH-PLAN §3.

### Android

- Live update / promoted ongoing notification path; not a 1:1 reproduction of iOS Live Activity.
- A foreground service may be required depending on use case (TECH-PLAN §4).
- Notification channel and POST_NOTIFICATIONS permission handling will be documented as the implementation lands.

## Project layout

```
src/                    TypeScript public API and native bridge
ios/                    Swift module + ObjC RCT_EXTERN bridge
android/                Kotlin module + ReactPackage
PRD.md                  Product scope, MVP, success criteria
TECH-PLAN.md            Architecture, API shape, phased build plan
```

## Roadmap

Phases tracked in [`TECH-PLAN.md`](./TECH-PLAN.md):

1. ✅ Repo scaffold + docs
2. ✅ JS API contract and type surface
3. ✅ iOS native module stub
4. ✅ Android native module stub
5. ⏳ Example app baseline
6. ⏳ iOS `startActivity` working path
7. ⏳ Android `startActivity` working path
8. ⏳ iOS `update` / `end` working path
9. ⏳ Android `update` / `end` working path
10. ⏳ Docs hardening

## License

MIT
