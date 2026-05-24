# React Native Live Activity PRD

## 1. Product Summary

### 1.1 Product Name
react-native-live-activity

### 1.2 One-line Summary
A React Native library that exposes iOS Live Activity and Android live update notification capabilities through a unified JS/TS API for real-time status surfaces.

### 1.3 Problem
- iOS Live Activity is useful for delivery, fitness, timer, transport, and progress-driven apps.
- Android also has promoted ongoing / live update notification patterns, but RN developers still have to wire native behaviors separately.
- React Native teams face repeated native complexity when trying to support real-time system surfaces on both platforms.
- Widget extension setup on iOS and notification/service behavior on Android create a lot of integration friction.

### 1.4 Solution
- Provide a React Native library with a minimal but useful JS API for starting, updating, and ending live status surfaces.
- On iOS, map to ActivityKit Live Activity.
- On Android, map to promoted ongoing / live update style notifications.
- Keep the JS surface consistent while still documenting platform differences honestly.

### 1.5 Target Users
- React Native app developers
- Teams building delivery / mobility / fitness / timer apps
- Developers who want real-time system UI surfaces without repeating native integration work

---

## 2. Goals
- Make iOS Live Activity usable from React Native with low integration friction.
- Support Android live update style notifications behind the same high-level library.
- Keep the initial API narrow and understandable.
- Provide example implementations for both platforms.

## 3. Non-Goals (Initial MVP)
- Perfect UI parity between iOS and Android
- Push-to-update support in first batch
- Turnkey Expo support in v1
- Arbitrary fully dynamic layout composition from JS
- A complete workflow/state-machine framework for all real-time surfaces

---

## 4. Core User Scenarios

### Scenario A. Start a live surface
1. Developer installs the library.
2. Configures iOS extension requirements and Android notification requirements.
3. Calls `startActivity(payload)` from JS.
4. User sees a system-level live status surface on the current platform.

### Scenario B. Update a live surface
1. App receives new progress/state.
2. Developer calls `updateActivity(id, payload)`.
3. Existing surface updates in place.

### Scenario C. End a live surface
1. App flow completes.
2. Developer calls `endActivity(id)`.
3. Live surface is dismissed gracefully.

---

## 5. MVP Feature Requirements

### 5.1 Platform Support Detection
**Priority:** P0

Requirements:
- Detect whether the current device/platform supports the library's live status surface.
- Return capability information rather than assuming parity.

Acceptance:
- [ ] JS can call `isSupported()`.
- [ ] iOS < 16.1 returns unsupported.
- [ ] Android support is reported based on available implementation path.

### 5.2 Start Activity
**Priority:** P0

Requirements:
- Start a live system surface from JS.
- Return a stable activity identifier.
- Support a minimal typed payload.

Acceptance:
- [ ] JS can start a live surface.
- [ ] Returned identifier can be used later.

### 5.3 Update Activity
**Priority:** P0

Requirements:
- Update an existing live surface by ID.
- Reflect changed state in native UI.

Acceptance:
- [ ] JS can update active content.

### 5.4 End Activity
**Priority:** P0

Requirements:
- End an active live surface by ID.
- Support graceful dismissal or stop behavior.

Acceptance:
- [ ] JS can end an activity.

### 5.5 Example App
**Priority:** P0

Requirements:
- Include a minimal example app.
- Demonstrate start / update / end flow.
- Show at least one platform-specific setup path clearly.

Acceptance:
- [ ] Developer can run example and test library locally.

### 5.6 Documentation
**Priority:** P0

Requirements:
- Explain iOS setup requirements.
- Explain Android setup requirements.
- Explain feature differences between platforms.

Acceptance:
- [ ] README covers installation and integration path.

---

## 6. API Design Principles
- Keep API intentionally small.
- Prefer explicit typed payloads over magic dynamic schema at first.
- Keep the top-level API unified, but document platform-specific differences clearly.
- Do not pretend the rendering models are identical.

### Candidate JS API
```ts
LiveActivity.isSupported(): Promise<boolean>
LiveActivity.startActivity(input): Promise<{ activityId: string }>
LiveActivity.updateActivity(activityId, input): Promise<void>
LiveActivity.endActivity(activityId): Promise<void>
```

Optional later expansion:
- `getPlatformCapabilities()`
- `requestPermissions()` where needed

---

## 7. Platform Strategy

### iOS
- Native implementation with ActivityKit
- Widget extension required
- Lock screen and Dynamic Island surfaces supported where available

### Android
- Native implementation using promoted ongoing / live update style notification patterns
- Foreground service may be needed depending on use case
- Notification-first UX rather than trying to fake iOS Live Activity exactly

---

## 8. Technical Risks
- iOS extension packaging and setup complexity
- Android implementation details differ by OS capabilities and notification constraints
- Bridge data modeling between JS and platform-specific state structures
- Developers may expect stronger cross-platform parity than the OSes really provide

---

## 9. MVP Scope

### Included
- iOS support detection
- Android support detection
- start / update / end API
- Example app
- Docs for native setup
- Cross-platform architectural baseline

### Excluded
- Remote push update support
- Expo plugin support
- Full UI parity across platforms
- Multiple highly customizable template families
- Rich asset pipeline and advanced visual composition system

---

## 10. Success Metrics
- Library can be installed into a bare React Native app successfully
- Example app can start/update/end a live surface on supported iOS and Android devices
- Setup docs are sufficient for first integration
- API feels coherent despite platform differences
