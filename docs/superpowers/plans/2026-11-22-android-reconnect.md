# Android getActiveActivities Reconnect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After an app process restart, Android `getActiveActivities()` returns the activities whose plain notifications are still showing, with `activityId`, `referenceId` and last `content`, and update/end keep working on them.

**Architecture:** Each plain notification carries `referenceId` and the raw `content` (JSON) in its extras; the activity id is already in its tag. A pure `LiveActivitySnapshots` object encodes/decodes that and merges the in-memory map with what is on screen; the module, and the timer receiver's redraw, use it.

**Tech Stack:** Kotlin, Android `NotificationManager.getActiveNotifications` (API 23+, the library's minSdk), `org.json` (platform), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-22-android-reconnect-design.md`

## Global Constraints

- Plain (non-foreground-service) notifications only; the foreground path is unchanged.
- No JS API change: `ActiveLiveActivity = { activityId, referenceId?, content }`.
- Extras keys: `com.woobottle.liveactivity.referenceId`, `com.woobottle.liveactivity.content`.
- Tag format stays `live_activity:<activityId>`.
- Within one process the in-memory map keeps today's behaviour; a map entry wins over a notification for the same id.
- Verify with `cd example/android && ./gradlew :woobottle_react-native-live-activity:testDebugUnitTest` (local; CI runs the same) and `npm run typecheck` at the root.

## Review Focus

- A `content` with a nested `timer` (`pauseAt` absent) and non-ASCII `title` round-trips through JSON unchanged in shape; numbers come back as `Double`.
- A notification with the tag but no content extra (posted by an older version of the library before an app update without a reboot) is skipped, not returned half-empty.
- The foreground-service notification (no tag) and other apps'/other tags' notifications are never returned.
- A malformed content extra (not JSON) is skipped, never throws out of `getActiveActivities`.
- `updateActivity` on an id missing from the map keeps the `referenceId` read from the posted notification.

---

### Task 1: Snapshot codec and merge rule

**Files:**
- Create: `android/src/main/java/com/woobottle/liveactivity/LiveActivitySnapshots.kt`
- Modify: `android/build.gradle` (add `testImplementation 'org.json:json:20250517'` — Android's own `org.json` is a stub in JVM tests)
- Test: `android/src/test/java/com/woobottle/liveactivity/LiveActivitySnapshotsTest.kt`

**Interfaces:**
- Produces:
  - `data class ActivitySnapshot(val activityId: String, val referenceId: String?, val content: HashMap<String, Any>)` (moved out of `LiveActivityModule`, now internal top-level)
  - `data class PostedNotification(val tag: String?, val referenceId: String?, val contentJson: String?)`
  - `object LiveActivitySnapshots { const val EXTRA_REFERENCE_ID; const val EXTRA_CONTENT; fun encodeContent(content: Map<String, Any?>): String; fun decodeContent(json: String): HashMap<String, Any>?; fun activityIdFromTag(tag: String?): String?; fun merge(known: Collection<ActivitySnapshot>, posted: List<PostedNotification>): List<ActivitySnapshot> }`

- [ ] **Step 1: Write the failing tests** — `LiveActivitySnapshotsTest`:
  - `content round-trips with a nested timer` — encode then decode `{title:"집중 10분", subtitle:"x", progress:0.5, timer:{startAt:1.0E12, endAt:1.0000006E12, state:"running"}}`; assert equal map, `progress` and timer numbers are `Double`.
  - `null values are dropped` — `{title:"a", subtitle:null}` decodes to `{title:"a"}`.
  - `malformed json decodes to null` — `decodeContent("not json") == null`.
  - `activity id comes from the live activity tag` — `"live_activity:abc"` → `"abc"`; `null`, `"other:abc"`, `"live_activity:"` → `null`.
  - `merge adds on-screen activities the map lacks` — known `[a]`, posted `[a(other content), b]` → `[a(known content), b]`, b with its referenceId and decoded content.
  - `merge skips posts without a usable content extra` — posted with `contentJson = null` or `"not json"` or a non-live-activity tag → not returned.

- [ ] **Step 2: Run, expect FAIL** (unresolved `LiveActivitySnapshots`).

- [ ] **Step 3: Implement** — `encodeContent` via `JSONObject(map)` (drop null values first); `decodeContent` walks `JSONObject`/`JSONArray` back to `HashMap`/`ArrayList`, every `Number` to `Double`, `JSONObject.NULL` dropped, returns `null` on `JSONException`. `merge` keeps `known` order first, then posted in list order.

- [ ] **Step 4: Run, expect PASS** (whole suite).

- [ ] **Step 5: Commit** `feat(android): encode activity snapshots for notifications`.

### Task 2: Carry the snapshot and read it back

**Files:**
- Modify: `LiveActivityNotifications.kt` (`build(..., snapshot: Bundle? = null)`, merged into the extras with the token)
- Modify: `LiveActivityModule.kt` (`showNotification(activityId, parsed, referenceId, rawContent)`; `getActiveActivities` merges; `updateActivity` recovers `referenceId`; snapshot class removed in favour of Task 1's)
- Modify: `LiveActivityTimerReceiver.kt` (copy the two extras from the posted notification into the rebuilt one)

**Interfaces:**
- Consumes: Task 1's `LiveActivitySnapshots`, `ActivitySnapshot`, `PostedNotification`.
- Produces: `fun LiveActivitySnapshots.bundle(referenceId: String?, content: Map<String, Any?>): Bundle` (Android-side helper, in the same file is fine) and a module-private `postedNotifications(): List<PostedNotification>` reading `activeNotifications` (`tag`, two extras).

- [ ] **Step 1:** Plain `startActivity` / `updateActivity` pass `referenceId` and `content.toHashMap()` to `showNotification`, which adds them as extras. `updateActivity` on an id missing from the map takes `referenceId` from the posted notification with tag `tag(activityId)`, then puts the snapshot in the map.
- [ ] **Step 2:** `getActiveActivities` returns `merge(map.values, postedNotifications())` and puts every recovered snapshot into the map; any exception reading notifications falls back to the map alone.
- [ ] **Step 3:** Receiver: when rebuilding a non-foreground notification, pass `Bundle` with the posted notification's two extras to `build`.
- [ ] **Step 4:** Assemble + whole unit suite green; commit `feat(android): recover activities from their notifications after a restart`.

### Task 3: Docs and device check

**Files:** `README.md` (status, platform-differences row, roadmap line), `CHANGELOG.md` `[Unreleased]` (Added), `docs/plans/2026-08-01-device-verification.md` (new section).

- [ ] **Step 1:** Update the docs per the spec's Docs section; the device list: start (with `referenceId`, with timer) → kill process only → relaunch → query returns it with content and `referenceId` → update keeps `referenceId` → end removes it; swipe then relaunch → absent; foreground-service activity → absent; timer past `endAt` while dead → still returned.
- [ ] **Step 2:** Run the device list on an emulator; tick what passes.
- [ ] **Step 3:** Commit `docs: document the Android reconnect`.
