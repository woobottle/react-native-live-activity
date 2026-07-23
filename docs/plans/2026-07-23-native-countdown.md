# Native Countdown Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an optional Live Activity timer that counts down natively on iOS, freezes while paused, resumes from a new deadline, and displays a persistent completion message at zero.

**Architecture:** Extend the shared TypeScript content schema with a discriminated timer union and mirror it in ActivityKit `ContentState`. The iOS bridge converts epoch milliseconds to `Date`, supplies the deadline as `staleDate`, and the Widget Extension selects a native countdown, fixed paused duration, or completion label from the timer state. Android remains backward compatible and ignores the optional timer payload in this iteration.

**Tech Stack:** TypeScript, React Native classic bridge, Swift, ActivityKit, WidgetKit, SwiftUI, Jest, XCTest/build verification.

---

### Task 1: Public timer contract

**Files:**
- Modify: `src/types.ts`
- Modify: `src/index.ts`
- Test: `example/__tests__/LiveActivity.test.tsx`

**Step 1: Write the failing test**

Add bridge tests that pass these two values through `startActivity` and
`updateActivity`:

```ts
const running = {
  title: 'Focus',
  timer: {
    state: 'running',
    endAt: 1_800_000_000_000,
    completionText: '10분 달성!',
  },
}

const paused = {
  title: 'Focus',
  timer: {
    state: 'paused',
    remainingSeconds: 542,
    completionText: '10분 달성!',
  },
}
```

Add a compile-only import fixture for `LiveActivityTimer` and
`LiveActivityTimerState` so missing public exports fail typecheck.

**Step 2: Run the tests to verify RED**

Run:

```sh
cd example
npm test -- --runInBand __tests__/LiveActivity.test.tsx
npx tsc --noEmit
```

Expected: TypeScript fails because the timer types are not defined/exported.

**Step 3: Implement the minimal contract**

Use a discriminated union:

```ts
export type LiveActivityTimer =
  | {
      state: 'running'
      endAt: number
      completionText?: string
    }
  | {
      state: 'paused'
      remainingSeconds: number
      completionText?: string
    }
  | {
      state: 'completed'
      completionText?: string
    }
```

Add `timer?: LiveActivityTimer` to `LiveActivityContent` and export the timer
types from `src/index.ts`.

**Step 4: Run tests to verify GREEN**

Run the Task 1 commands again.

Expected: bridge tests and TypeScript compilation pass.

**Step 5: Commit**

```sh
git add src/types.ts src/index.ts example/__tests__/LiveActivity.test.tsx
git commit -m "feat: add live activity timer types"
```

### Task 2: iOS timer parsing and ActivityKit deadline

**Files:**
- Modify: `ios/LiveActivityAttributes.swift`
- Modify: `ios/LiveActivityModule.swift`
- Create: `tests/ios/LiveActivityTimerModelTests.swift`

**Step 1: Write the failing test**

Create a Swift test harness covering:

- running timer preserves `endAt`;
- paused timer preserves `remainingSeconds`;
- completed timer needs no numeric field;
- default completion text is `10분 달성!`;
- negative paused seconds are rejected;
- running timer without `endAt` is rejected.

The harness must compile against the shared timer model rather than duplicating
its logic.

**Step 2: Run the test to verify RED**

Run an `xcrun swiftc` command targeting the installed iOS Simulator SDK, then
run the resulting test executable where supported.

Expected: compilation fails because the shared timer model/parser does not yet
exist.

**Step 3: Implement the minimal native model**

Add a Codable/Hashable timer value to
`LiveActivityAttributes.ContentState`. Parse the nested `timer` dictionary in
`LiveActivityModule`, validating the state-specific required fields and
converting epoch milliseconds to `Date`.

Add a helper that returns the running timer's deadline. Use it as:

```swift
ActivityContent(state: state, staleDate: state.timer?.endAt)
```

for start/update calls on iOS 16.2 and later. Preserve the existing iOS 16.1
fallback APIs.

**Step 4: Run tests to verify GREEN**

Re-run the Swift harness.

Expected: all timer model cases pass.

**Step 5: Commit**

```sh
git add ios/LiveActivityAttributes.swift ios/LiveActivityModule.swift tests/ios
git commit -m "feat(ios): bridge native live activity timer state"
```

### Task 3: Widget countdown presentation

**Files:**
- Modify: `example/ios/LiveActivityWidget/LiveActivityWidgetLiveActivity.swift`
- Test: `tests/ios/LiveActivityTimerPresentationTests.swift`

**Step 1: Write the failing test**

Test a pure presentation formatter for:

- 0 seconds → `00:00`;
- 542 seconds → `09:02`;
- 3,661 seconds → `1:01:01`;
- paused state selects fixed duration;
- completed/stale running state selects completion text.

**Step 2: Run the test to verify RED**

Run the Swift presentation harness.

Expected: compilation fails because the presentation helper does not exist.

**Step 3: Implement the minimal Widget UI**

Render a native SwiftUI timer for an active running deadline, a fixed formatted
duration when paused, and `completionText` for completed or stale content.
Apply appropriately sized variants to Lock Screen and all Dynamic Island
regions.

**Step 4: Run tests to verify GREEN**

Re-run the presentation harness and build the Widget target.

Expected: formatter tests and Widget compilation pass.

**Step 5: Commit**

```sh
git add example/ios/LiveActivityWidget tests/ios
git commit -m "feat(ios): render native live activity countdown"
```

### Task 4: Example pause/resume state machine

**Files:**
- Modify: `example/App.tsx`
- Modify: `example/__tests__/App.test.tsx`

**Step 1: Write the failing tests**

Use fake timers and mocked native methods to verify:

- Start sends a deadline ten minutes in the future;
- Pause computes and sends frozen remaining seconds;
- Resume computes a new deadline from the frozen remainder;
- completion does not call `endActivity`;
- End explicitly calls `endActivity`.

**Step 2: Run tests to verify RED**

Run:

```sh
cd example
npm test -- --runInBand __tests__/App.test.tsx
```

Expected: tests fail because Pause/Resume controls and timer payloads do not
exist.

**Step 3: Implement the minimal example flow**

Add Start, Pause/Resume, and End controls. Keep timer bookkeeping in the example
app, send normal `updateActivity()` calls for transitions, and leave the Live
Activity active when the ten-minute deadline is reached.

**Step 4: Run tests to verify GREEN**

Re-run the App tests.

Expected: all state-machine tests pass.

**Step 5: Commit**

```sh
git add example/App.tsx example/__tests__/App.test.tsx
git commit -m "feat(example): demonstrate pauseable native countdown"
```

### Task 5: Documentation and setup contract

**Files:**
- Modify: `README.md`
- Modify: `PRD.md`

**Step 1: Write the documentation acceptance checklist**

Verify the docs cover:

- timer payload examples for running, paused, resumed, and completed states;
- epoch-millisecond units;
- default completion message;
- explicit `endActivity()` behavior;
- iOS 16.2 stale transition and iOS 16.1 fallback;
- Android limitation;
- Widget Extension shared-model setup.

**Step 2: Update documentation**

Add the timer contract and examples, update platform differences, remove the
stale Xcode blocker status, and mark only verified PRD acceptance items.

**Step 3: Verify documentation**

Run:

```sh
rg -n "endAt|remainingSeconds|10분 달성|iOS 16.2|Android" README.md PRD.md
```

Expected: every checklist topic has a documented match.

**Step 4: Commit**

```sh
git add README.md PRD.md
git commit -m "docs: document native countdown lifecycle"
```

### Task 6: Full verification

**Files:**
- Modify as needed from verification findings only.

**Step 1: Install locked dependencies**

Install root and example dependencies without changing dependency versions.
Run CocoaPods for the example app.

**Step 2: Run static and JS verification**

```sh
npm run typecheck
cd example
npm test -- --runInBand
npm run lint
```

Expected: zero failures and zero lint errors.

**Step 3: Run Android regression build**

```sh
cd example/android
./gradlew :app:assembleDebug --no-daemon
```

Expected: build succeeds, proving the optional timer did not break Android.

**Step 4: Run iOS builds**

Build `LiveActivityExample` and `LiveActivityWidget` against an available
simulator or generic iOS Simulator destination with code signing disabled.

Expected: both targets compile successfully.

**Step 5: Inspect the final diff**

```sh
git diff --check
git status --short
git log --oneline -8
```

Expected: no whitespace errors; only intentional changes remain.
