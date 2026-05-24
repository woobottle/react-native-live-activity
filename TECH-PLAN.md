# React Native Live Activity Technical Plan

## 1. Architecture

### JS Layer
- TypeScript API surface
- Public module methods:
  - `isSupported`
  - `startActivity`
  - `updateActivity`
  - `endActivity`
- Input/output type definitions

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

### Example App
- Buttons/actions to start/update/end
- Sample payload editor or fixed demo payload
- Clear setup notes

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

---

## 3. iOS Notes
- Minimum iOS support for Live Activity: 16.1+
- ActivityKit must only be used where available
- Widget extension target will likely be required in the example app
- Packaging strategy must document how consuming apps add the extension

---

## 4. Android Strategy
Initial MVP:
- return unsupported

Future path:
- optional Android module using foreground service + ongoing notification
- do not force same UI promises as iOS Live Activity

---

## 5. Suggested Build Phases
1. Repo scaffold + docs
2. JS API contract and type surface
3. iOS native module stub
4. example iOS app with extension target
5. startActivity working path
6. updateActivity working path
7. endActivity working path
8. docs hardening

---

## 6. Open Questions
- Should library use classic bridge first or aim for TurboModule immediately?
- Should v1 ship a single opinionated activity content schema or allow developer-defined schema earlier?
- How much of extension setup can realistically be automated?

## 7. Recommendation
Start with:
- classic RN native module
- one small opinionated content schema
- bare React Native example app
- iOS only in first working release
