# Android `getActiveActivities` reconnect after a process restart — design

Status: draft, 2026-11-22

## Why

Reduce the remaining iOS/Android difference. After the app process restarts,
iOS `getActiveActivities()` returns the still-running activities (ActivityKit
owns them), while Android returns `[]`: its snapshot lives only in the module's
in-memory map. The plain notification itself usually survives the restart, so
the app is left with a notification on screen that it can no longer find,
update or end by `referenceId`.

## Goals

- After a process restart, `getActiveActivities()` on Android returns every
  activity whose **plain** (non-foreground-service) notification is still
  showing, with its `activityId`, `referenceId` and last `content`.
- `updateActivity` / `endActivity` work on such a recovered activity, and its
  `referenceId` survives the update.
- After a restart, a notification the user swiped away is not returned (as on
  iOS, where a dismissed activity is gone). Within one process the map keeps
  today's behaviour.

## Non-goals

- Foreground-service activities. When the process dies the service stops itself
  and its notification goes away; they stay absent after a restart, as today.
  (Restoring them is a separate change with service-restart and Android 14+
  background-start constraints; chosen against for now.)
- Surviving a force stop, a reboot, or an app update — all clear the
  notifications, so there is nothing to recover.
- Any JS API change. `ActiveLiveActivity` keeps its shape.

## Approaches

1. **Embed the snapshot in the notification, merge with the in-memory map
   (chosen).** Each plain notification carries the activity's `referenceId`
   and raw `content` (JSON) in its extras. `getActiveActivities()` returns the
   in-memory map plus any of the app's live-activity notifications on screen
   that the map lacks. The screen is the source of truth for recovery, so a
   swiped-away notification drops out on its own.
2. SharedPreferences record. Outlives the notification (swipe, force stop), so
   it must be reconciled with what is on screen anyway — more code, no gain.
3. Notifications only, no map. Simplest, but `notify` is asynchronous: an
   activity started a moment ago can be missing from an immediate query.

## Design

- **What a notification carries** (plain path only; the foreground path is
  unchanged): extras `com.woobottle.liveactivity.referenceId` (when set) and
  `com.woobottle.liveactivity.content` (the raw `content` map as JSON, the same
  value `getActiveActivities` returns today). The `activityId` already lives in
  the tag `live_activity:<activityId>`.
- **Reading back**: `getActiveActivities()` = map values, plus, for each
  `activeNotifications` entry whose tag starts with `live_activity:` and whose
  activity id is not in the map and which has a content extra, a recovered
  snapshot. A recovered snapshot is also put into the map, so later calls in
  this process behave like any other activity.
- **Update / end of an activity not in the map** (the app kept the id across
  the restart and never queried): `updateActivity` takes the `referenceId` from
  the posted notification's extras before reposting, so it is not lost; then
  the activity joins the map. `endActivity` already cancels by tag/id and needs
  no snapshot.
- **Timer alarm redraw** (`LiveActivityTimerReceiver`): it rebuilds the
  notification from the alarm payload, which lacks the snapshot. It copies the
  two snapshot extras from the posted notification into the rebuilt one, so a
  recovered activity whose timer has passed `endAt` still reports its content.
- **JSON round trip**: `content` is a JS object of strings, numbers, booleans,
  a nested `timer` object; `null`s are dropped the same way `toHashMap` does
  today. Numbers come back as doubles, matching what the bridge already returns.
- **Pure parts are JVM-testable**: tag ↔ activity id, snapshot JSON encode/decode
  (`content` and `referenceId`), and the merge rule (map wins over notification
  for the same id; notifications without a content extra are skipped).

## Docs

README status paragraph, the **Platform behavior differences** row for
`getActiveActivities()` after a restart, and the roadmap line; CHANGELOG
`[Unreleased]`; a device-check list in `docs/plans/2026-08-01-device-verification.md`
(start → kill the process → relaunch → query, update, end; swipe → relaunch →
absent; foreground-service activity → absent).

## Left to the builder

Names of the helper object and extras keys beyond the two above, JSON
library (`org.json` is on the platform), where the merge lives.
