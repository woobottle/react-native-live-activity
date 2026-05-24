# React Native Live Activity Technical Plan

## 1. Architecture

### JS Layer
- TypeScript API surface
- Public module methods:
  - `isSupported`
  - `startActivity`
  - `updateActivity`
  - `endActivity`
- Optional later method:
  - `getPlatformCapabilities`
- Shared input/output type definitions

### Native iOS Layer
- Swift native module bridge for RN
- ActivityKit integration
- Shared activity model definitions
- Helper for locating and managing active activities

### Widget Extension Layer
- ActivityAttributes definition
- ContentState definition
- ActivityConfiguration UI
- Dynamic Island presentation where supported

### Native Android Layer
- Kotlin native module bridge for RN
- Ongoing notification / live update notification path
- Notification manager integration
- Optional foreground service support depending on implementation needs

### Example App
- Buttons/actions to start/update/end
- Sample payload editor or fixed demo payload
- Clear setup notes for both platforms

---

## 2. Initial API Shape

```ts
export type LiveActivityContent = {
  title: string
  subtitle?: string
  progress?: number
}

export type StartLiveActivityResult = {
  activityId: string
}
```

Possible first public methods:
- `isSupported(): Promise<boolean>`
- `startActivity(content: LiveActivityContent): Promise<StartLiveActivityResult>`
- `updateActivity(activityId: string, content: LiveActivityContent): Promise<void>`
- `endActivity(activityId: string): Promise<void>`

Later:
- `getPlatformCapabilities(): Promise<{ iosLiveActivity: boolean; androidLiveUpdate: boolean }>`

---

## 3. iOS Notes
- Minimum iOS support for Live Activity: 16.1+
- ActivityKit must only be used where available
- Widget extension target will likely be required in the example app
- Packaging strategy must document how consuming apps add the extension

---

## 4. Android Notes
- Android implementation should center on live update / promoted ongoing notification patterns
- Foreground service may be necessary depending on the scenario
- The library should expose a common lifecycle API without promising identical presentation
- Need to model notification channel and permission/setup needs clearly in docs

---

## 5. Suggested Build Phases
1. Repo scaffold + docs refresh
2. JS API contract and type surface
3. iOS native module stub
4. Android native module stub
5. example app baseline
6. iOS startActivity working path
7. Android startActivity working path
8. iOS update/end working path
9. Android update/end working path
10. docs hardening

---

## 6. Open Questions
- Should library use classic bridge first or aim for TurboModule immediately?
- Should v1 ship one opinionated content schema or allow configurable schema earlier?
- How much of iOS extension setup can realistically be automated?
- How opinionated should Android notification/channel/service setup be?

## 7. Recommendation
Start with:
- classic RN native module
- one small opinionated content schema
- bare React Native example app
- iOS + Android baseline from the start
- honest docs about platform behavior differences
