# Native Countdown Design

## Goal

Extend `react-native-live-activity` with an optional timer payload that lets an
iOS Live Activity render a countdown without the React Native app remaining
active. The countdown follows the consuming app's timer state: it counts down
while running, freezes while paused, resumes from the frozen remainder, and
shows a completion message at zero until the activity is explicitly ended.

## Public API

Add an optional nested timer to `LiveActivityContent`:

```ts
export type LiveActivityTimer = {
  state: 'running' | 'paused' | 'completed'
  endAt?: number
  remainingSeconds?: number
  completionText?: string
}

export type LiveActivityContent = {
  title: string
  subtitle?: string
  progress?: number
  timer?: LiveActivityTimer
}
```

`endAt` is Unix epoch time in milliseconds. The field is required when
`state` is `running`. `remainingSeconds` is required when `state` is `paused`.
`completionText` defaults to `10분 달성!`.

The library renders state supplied by the consuming app; it does not own the
mission timer state machine. To resume a paused timer, the app computes a new
`endAt` from the frozen `remainingSeconds` and sends a normal
`updateActivity()` call.

## iOS Data Flow

The bridge parses the nested timer payload into
`LiveActivityAttributes.ContentState`. A running timer's `endAt` becomes the
ActivityKit content's `staleDate` on iOS 16.2 and later. This gives the system a
native deadline even when the app is suspended.

The Widget Extension renders:

- `running`, before the deadline: SwiftUI's native countdown text;
- `running`, after ActivityKit marks the content stale: `completionText`;
- `paused`: a fixed `MM:SS` or `H:MM:SS` value derived from
  `remainingSeconds`;
- `completed`: `completionText`.

The same timer presentation is used in the Lock Screen, expanded Dynamic
Island, compact Dynamic Island, and minimal Dynamic Island with layouts sized
for each surface.

iOS 16.1 retains the native countdown display, but automatic completion-message
switching cannot use `staleDate`; the enhanced deadline transition is therefore
available on iOS 16.2 and later.

## Android Behavior

This iteration does not claim app-independent Android countdown behavior.
Android continues rendering the existing title, subtitle, and progress
notification and safely ignores the optional timer field. The README documents
this platform difference.

## Validation and Errors

Native iOS parsing rejects:

- unsupported timer states;
- a running timer without a finite positive `endAt`;
- a paused timer without a finite, non-negative `remainingSeconds`;
- non-string `completionText`.

Existing content without a timer remains fully backward compatible.

## Testing

Tests are added before production changes:

- TypeScript compile coverage for the exported timer API;
- JS bridge forwarding coverage for running and paused timer payloads;
- iOS parsing/model tests where the current project structure permits;
- example-app coverage for start, pause, resume, and end state transitions;
- typecheck, Jest, lint, Android build, and iOS build verification.

Manual device verification covers the system-rendered countdown while the app
is backgrounded and the stale deadline transition to `10분 달성!`.
