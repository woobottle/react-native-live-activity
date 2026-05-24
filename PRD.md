# React Native Live Activity PRD

## 1. Product Summary

### 1.1 Product Name
react-native-live-activity

### 1.2 One-line Summary
A React Native library that exposes Apple's iOS Live Activity capabilities through a clean JS/TS API, with a future-compatible Android fallback strategy.

### 1.3 Problem
- iOS Live Activity is powerful for delivery, fitness, timer, transport, and progress-driven apps.
- React Native developers currently face heavy native setup complexity when trying to adopt ActivityKit.
- Widget extension setup, ActivityAttributes modeling, and native bridge design are painful to repeat per app.
- There is value in a reusable RN-native bridge that reduces this setup burden.

### 1.4 Solution
- Provide a React Native library with a minimal but useful JS API for starting, updating, and ending Live Activities.
- Ship native iOS bridge foundations for ActivityKit and WidgetKit extension integration.
- Provide a clear architecture for how Android can later map into foreground notification based status surfaces.

### 1.5 Target Users
- React Native app developers
- Teams building delivery / mobility / fitness / timer apps
- Developers who want Live Activity support without rewriting native integration from scratch

---

## 2. Goals
- Make iOS Live Activity usable from React Native with low integration friction.
- Keep the initial API narrow and easy to understand.
- Provide an example app and extension integration pattern.
- Leave room for a later Android adapter without forcing false parity in v1.

## 3. Non-Goals (Initial MVP)
- Full Android parity with iOS Live Activity UX
- Push-to-update support in first batch
- Turnkey Expo support in v1
- Highly dynamic arbitrary UI composition from JS
- Full activity templating engine

---

## 4. Core User Scenarios

### Scenario A. Start a live activity
1. Developer installs the library.
2. Configures the required iOS extension target.
3. Calls `startActivity(payload)` from JS.
4. User sees Live Activity appear on lock screen / Dynamic Island.

### Scenario B. Update a live activity
1. App receives new progress/state.
2. Developer calls `updateActivity(id, payload)`.
3. Existing Live Activity refreshes.

### Scenario C. End a live activity
1. App flow completes.
2. Developer calls `endActivity(id)`.
3. Live Activity ends gracefully.

---

## 5. MVP Feature Requirements

### 5.1 Platform Support Detection
**Priority:** P0

Requirements:
- Detect whether current platform and OS version support Live Activity.
- Return `false` on unsupported platforms.

Acceptance:
- [ ] JS can call `isSupported()`.
- [ ] iOS < 16.1 returns unsupported.
- [ ] Android returns unsupported in MVP.

### 5.2 Start Activity
**Priority:** P0

Requirements:
- Start a Live Activity from JS.
- Return a stable activity identifier.
- Support a minimal typed payload.

Acceptance:
- [ ] JS can start an activity.
- [ ] Returned identifier can be used later.

### 5.3 Update Activity
**Priority:** P0

Requirements:
- Update an existing Live Activity by ID.
- Reflect changed state in native UI.

Acceptance:
- [ ] JS can update active activity content.

### 5.4 End Activity
**Priority:** P0

Requirements:
- End an active Live Activity by ID.
- Support graceful dismissal.

Acceptance:
- [ ] JS can end an activity.

### 5.5 Example App
**Priority:** P0

Requirements:
- Include a minimal example app.
- Demonstrate start / update / end flow.

Acceptance:
- [ ] Developer can run example and test the library locally.

### 5.6 Documentation
**Priority:** P0

Requirements:
- Explain setup requirements.
- Explain iOS extension requirement.
- Explain Android limitation and future fallback strategy.

Acceptance:
- [ ] README covers installation and integration path.

---

## 6. API Design Principles
- Keep API intentionally small.
- Prefer explicit typed payloads over magic dynamic schema at first.
- Avoid pretending iOS and Android are identical.
- Use graceful unsupported behavior where platform parity does not exist yet.

### Candidate JS API
```ts
LiveActivity.isSupported(): Promise<boolean>
LiveActivity.startActivity(input): Promise<{ activityId: string }>
LiveActivity.updateActivity(activityId, input): Promise<void>
LiveActivity.endActivity(activityId): Promise<void>
```

---

## 7. Platform Strategy

### iOS
- Native implementation with ActivityKit
- Widget extension required
- Lock screen and Dynamic Island surfaces supported where available

### Android
- Not implemented in MVP
- Long-term path: foreground service + ongoing notification abstraction
- API may later evolve into a broader “live status surface” abstraction if needed

---

## 8. Technical Risks
- iOS extension packaging and setup complexity
- Bridge data modeling between JS and ActivityAttributes/ContentState
- Limited UI flexibility in ActivityKit compared with React rendering expectations
- Confusion from developers expecting Android parity too early

---

## 9. MVP Scope

### Included
- iOS support detection
- start / update / end API
- Example app
- Docs for native setup
- Initial architecture for future Android fallback

### Excluded
- Android implementation
- Remote push update support
- Expo plugin support
- Multiple customizable activity template families
- Rich asset pipeline and highly dynamic layout system

---

## 10. Success Metrics
- Library can be installed into a bare React Native app successfully
- Example app can start/update/end Live Activity on supported iOS device
- Setup docs are sufficient for first integration
- Low-friction API validated by initial internal use
