# Android Native Countdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android가 `content.timer`를 iOS와 같은 규칙으로 검증하고, 알림 크로노미터로 카운트다운하며, `endAt`에 알람으로 00:00 고정 상태로 전환한다.

**Architecture:** 파싱(`LiveActivityContentParser`) → 순수 표현 결정(`LiveActivityTimerPresentation.present`) → 알림 빌드(`LiveActivityNotifications.build`). 다음 전환 시각이 있으면 `LiveActivityTimerScheduler`가 알람을 걸고, `LiveActivityTimerReceiver`가 Intent extras만으로 알림을 다시 만들어 덮어쓴다. 파싱과 표현은 JVM 유닛테스트, 프레임워크 부분은 실기기 검증 문서로 다룬다.

**Tech Stack:** Kotlin, Android `Notification.Builder` / `AlarmManager` / `BroadcastReceiver`, JUnit 4 + `JavaOnlyMap`.

**Spec:** `docs/superpowers/specs/2026-11-15-android-countdown-design.md`

## Global Constraints

- `minSdkVersion` 23. 크로노미터 카운트다운(`setChronometerCountDown`)은 API 24+에서만 호출.
- timer 에러 메시지는 `ios/LiveActivityContentParser.swift`의 `LiveActivityContentError.message`와 글자 단위로 동일.
- 검증은 원본 `Double`로, 저장은 통과 후 `toLong()`.
- 매니페스트에 `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`을 선언하지 않는다.
- 정확한 알람은 `SDK_INT < 31 || alarmManager.canScheduleExactAlarms()`일 때만. 그 외 `setWindow(RTC_WAKEUP, atMs, 600_000L, pi)`.
- 고정 텍스트 형식: 1시간 미만 `mm:ss`, 이상 `h:mm:ss`, 초는 내림.
- 알림이 이미 사라졌으면 Receiver는 다시 게시하지 않는다.
- 알람 예약 실패는 start/update를 실패시키지 않는다 (`Log.w`만).
- title/progress의 기존 에러 메시지는 바꾸지 않는다.

테스트 명령 (모든 태스크 공통, 저장소 루트에서):

```bash
cd example/android && ./gradlew :woobottle_react-native-live-activity:testDebugUnitTest --console=plain
cd example/android && ./gradlew :woobottle_react-native-live-activity:assembleDebug --console=plain
```

## Review Focus

JVM 테스트가 닿지 않는 조건들이다. 프레임워크 의존이라 Task 5의 실기기 검증 항목으로 고정한다. Task 2에 테스트로 고정할 수 있는 것만 테스트로 둔다.

1. **사용자가 알림을 스와이프로 지웠거나 End한 뒤 알람이 울림**: 알림이 다시 뜨면 안 된다. (Task 4 구현 + Task 5 기기 항목)
2. **앱 프로세스가 죽은 상태에서 `endAt` 도달**: Receiver가 extras만으로 00:00 알림을 만든다. (Task 4 + Task 5)
3. **timer가 있던 활동에 timer 없는 update**: 이전 알람이 취소되어 나중에 옛 내용으로 덮어쓰지 않는다. (Task 4 + Task 5)
4. **`state: running`인데 `pauseAt`도 함께 옴** (iOS 파서는 범위만 맞으면 허용): `pauseAt`을 무시하고 카운트다운. (Task 2 테스트)
5. **`startAt`이 미래인 running**: 전체 길이 고정 → `startAt`에 크로노미터로 전환 → `endAt`에 00:00. (Task 2 테스트 + Task 5 기기 항목)

---

### Task 1: timer 파싱과 검증

**Files:**
- Modify: `android/src/main/java/com/woobottle/liveactivity/LiveActivityContentParser.kt`
- Test: `android/src/test/java/com/woobottle/liveactivity/LiveActivityContentParserTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class TimerState { RUNNING, PAUSED, COMPLETED }   // LiveActivityContentParser 안에 중첩
  data class ParsedTimer(val startAtMs: Long, val endAtMs: Long, val pauseAtMs: Long?, val state: TimerState)
  data class ParsedContent(val title: String, val subtitle: String?, val progress: Int?, val timer: ParsedTimer?)
  ```
  JS 문자열 ↔ enum: `"running"`/`"paused"`/`"completed"` ↔ `RUNNING`/`PAUSED`/`COMPLETED`.

- [ ] **Step 1: 실패하는 테스트 작성** — 기존 `content(...)` 헬퍼에 `timer(...)` 헬퍼(`JavaOnlyMap.of`)를 추가하고 아래를 작성한다. 거부 케이스는 `assertThrows(IllegalArgumentException::class.java)`의 `.message`를 `assertEquals`로 확인한다.

  | 테스트 | 입력 timer | 기대 |
  |---|---|---|
  | `parses no timer as null` | 키 없음 / `"timer" to null` | `timer == null` |
  | `parses a running timer` | `startAt=1000.0, endAt=61000.9, state="running"` | `ParsedTimer(1000, 61000, null, RUNNING)` |
  | `accepts endAt equal to startAt` | `startAt=endAt=1000.0, running` | 통과 |
  | `accepts pauseAt on either boundary` | `pauseAt=startAt`, `pauseAt=endAt`, paused | 통과 |
  | `rejects a non-object timer` | `"timer" to "x"` | `content.timer must be an object` |
  | `rejects missing or boolean bounds` | `startAt` 없음 / `startAt=true` | `content.timer startAt and endAt must be finite numbers` |
  | `rejects non-finite bounds` | `endAt=Double.NaN` / `Double.POSITIVE_INFINITY` | 위와 동일 |
  | `rejects endAt before startAt` | `startAt=2000.0, endAt=1000.0` | `content.timer endAt must be after startAt` |
  | `compares bounds before truncating` | `startAt=100.7, endAt=100.3` | `content.timer endAt must be after startAt` |
  | `rejects an unknown state` | `state="stopped"` / `state=1.0` | `content.timer state is invalid` |
  | `rejects a non-numeric pauseAt` | `pauseAt="x"`, running | `content.timer startAt and endAt must be finite numbers` |
  | `rejects paused without pauseAt` | paused, `pauseAt` 없음 | `content.timer pauseAt is required when paused` |
  | `rejects pauseAt outside the interval` | running, `pauseAt=endAt+1` | `content.timer pauseAt must be within the timer interval` |

- [ ] **Step 2: 실패 확인** — 테스트 명령 실행. Expected: 컴파일 실패 (`timer`/`ParsedTimer` 없음).
- [ ] **Step 3: 구현** — `parse()`에 `timer = content.optionalTimer("timer")`. 검증 순서는 위 표의 위→아래(= iOS `parseTimer` 순서). `pauseAt` 범위 검사는 `state`와 무관하게 수행.
- [ ] **Step 4: 통과 확인** — 테스트 명령. Expected: 전체 PASS.
- [ ] **Step 5: 커밋** — `feat(android): parse and validate content.timer with the iOS rules`

### Task 2: 표현 결정 (순수 Kotlin)

**Files:**
- Create: `android/src/main/java/com/woobottle/liveactivity/LiveActivityTimerPresentation.kt`
- Test: `android/src/test/java/com/woobottle/liveactivity/LiveActivityTimerPresentationTest.kt`

**Interfaces:**
- Consumes: Task 1의 `ParsedTimer`, `TimerState`.
- Produces (`object LiveActivityTimerPresentation`, 최상위 sealed/data class는 같은 파일):
  ```kotlin
  sealed class TimerDisplay {
    data class Countdown(val untilMs: Long) : TimerDisplay()
    data class Fixed(val text: String) : TimerDisplay()
    data class EndsAt(val atMs: Long) : TimerDisplay()
  }
  data class TimerPresentation(val display: TimerDisplay, val nextTransitionAtMs: Long?)
  fun present(timer: ParsedTimer, nowMs: Long, sdkInt: Int): TimerPresentation
  fun format(durationMs: Long): String
  ```
  Android 프레임워크 import 금지 (API 레벨은 숫자 리터럴 `24`로 비교).

- [ ] **Step 1: 실패하는 테스트 작성** — `start=0, end=600_000` (10분) 기준.

  | 테스트 | 입력 | 기대 |
  |---|---|---|
  | `before start shows the full duration` | running, `now=-5_000`, sdk 34 | `Fixed("10:00")`, next=`0` |
  | `running counts down to endAt` | running, `now=1_000`, sdk 34 | `Countdown(600_000)`, next=`600_000` |
  | `running on API 23 shows the end time` | running, `now=1_000`, sdk 23 | `EndsAt(600_000)`, next=`600_000` |
  | `running past endAt freezes at zero` | running, `now=600_000` | `Fixed("00:00")`, next=`null` |
  | `running ignores pauseAt` | running, `pauseAt=300_000`, `now=1_000`, sdk 34 | `Countdown(600_000)` |
  | `paused shows the remaining time` | paused, `pauseAt=200_500` | `Fixed("06:39")`, next=`null` (sdk 23·34 둘 다) |
  | `completed shows zero` | completed | `Fixed("00:00")`, next=`null` |
  | `format pads minutes below an hour` | `59_900` / `0` / `599_999` | `"00:59"` / `"00:00"` / `"09:59"` |
  | `format adds hours from one hour` | `3_600_000` / `36_061_000` | `"1:00:00"` / `"10:01:01"` |
  | `format clamps negatives to zero` | `-1` | `"00:00"` |

- [ ] **Step 2: 실패 확인** — 테스트 명령. Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — 스펙 §2 표 그대로.
- [ ] **Step 4: 통과 확인** — Expected: 전체 PASS.
- [ ] **Step 5: 커밋** — `feat(android): decide how a timer renders at a given moment`

### Task 3: 알림에 timer 렌더링 (두 경로)

**Files:**
- Modify: `android/src/main/java/com/woobottle/liveactivity/LiveActivityNotifications.kt` (`build`)
- Modify: `android/src/main/java/com/woobottle/liveactivity/LiveActivityModule.kt` (start/update, `showNotification`)
- Modify: `android/src/main/java/com/woobottle/liveactivity/LiveActivityForegroundService.kt` (`start`, `onStartCommand`)

**Interfaces:**
- Consumes: Task 1 `ParsedContent.timer`, Task 2 `present`, `TimerDisplay`.
- Produces:
  ```kotlin
  // LiveActivityNotifications
  fun build(context: Context, title: String, subtitle: String?, progress: Int?, timer: TimerDisplay?): Notification
  // LiveActivityForegroundService.Companion
  fun start(context: Context, activityId: String, title: String, subtitle: String?, progress: Int?, timer: ParsedTimer?)
  // timer extras helpers (Task 4 Receiver도 재사용) — LiveActivityTimerExtras.kt (internal object)
  fun put(intent: Intent, timer: ParsedTimer)       // startAtMs, endAtMs, state(name), pauseAtMs는 non-null일 때만
  fun read(intent: Intent): ParsedTimer?            // startAtMs 키 없으면 null; pauseAtMs는 hasExtra로 판별
  ```

- [ ] **Step 1: `build`에 timer 분기 추가** — 스펙 §3: `Countdown` → `setShowWhen(true).setWhen(untilMs).setUsesChronometer(true)` + API 24+에서 `setChronometerCountDown(true)`; `Fixed` → `setSubText(text)`; `EndsAt` → `setShowWhen(true).setWhen(atMs)`; `null` → 기존과 동일.
- [ ] **Step 2: `LiveActivityTimerExtras.kt` 작성** (위 시그니처).
- [ ] **Step 3: 모듈 연결** — 일반 경로: `parsed.timer?.let { present(it, System.currentTimeMillis(), Build.VERSION.SDK_INT) }?.display`를 `build`에 전달. 포그라운드 경로: `LiveActivityForegroundService.start(..., parsed.timer)`; 서비스는 `LiveActivityTimerExtras.read(intent)`로 복원해 자기 시점에서 `present`.
- [ ] **Step 4: 빌드와 기존 테스트 확인** — assemble + test 명령. Expected: BUILD SUCCESSFUL, 전체 PASS.
- [ ] **Step 5: 커밋** — `feat(android): render content.timer in the ongoing notification`

### Task 4: 전환 알람

**Files:**
- Create: `android/src/main/java/com/woobottle/liveactivity/LiveActivityTimerScheduler.kt`
- Create: `android/src/main/java/com/woobottle/liveactivity/LiveActivityTimerReceiver.kt`
- Modify: `android/src/main/AndroidManifest.xml` (`<receiver android:name=".LiveActivityTimerReceiver" android:exported="false" />`)
- Modify: `LiveActivityModule.kt`, `LiveActivityForegroundService.kt`

**Interfaces:**
- Consumes: Task 2 `present`, Task 3 `LiveActivityTimerExtras`, `LiveActivityNotifications.build/tag/id`.
- Produces:
  ```kotlin
  internal object LiveActivityTimerScheduler {
    data class Payload(val activityId: String, val title: String, val subtitle: String?, val progress: Int?, val timer: ParsedTimer, val foreground: Boolean)
    fun schedule(context: Context, payload: Payload, atMs: Long)
    fun cancel(context: Context, activityId: String)
  }
  class LiveActivityTimerReceiver : BroadcastReceiver()
  ```

- [ ] **Step 1: 스케줄러** — `PendingIntent.getBroadcast(context, LiveActivityNotifications.id(activityId), intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)`. 정확/`setWindow` 분기는 Global Constraints대로, `SecurityException` 시 `setWindow` 재시도. 실패는 `Log.w`.
- [ ] **Step 2: Receiver** — 스펙 §4의 1–4단계. 활성 확인은 `notificationManager.activeNotifications.any { it.id == id && it.tag == expectedTag }` (일반: `tag(activityId)`, 포그라운드: `null`). 없으면 return. 있으면 같은 tag/id로 `notify`; `nextTransitionAtMs`가 있으면 같은 payload로 `schedule`.
- [ ] **Step 3: 연결** — 게시하는 쪽이 정한다: 일반 경로는 모듈의 start/update, 포그라운드 경로는 서비스 `onStartCommand`. `nextTransitionAtMs != null`이면 `schedule`, 아니면(timer 없음 포함) `cancel`. `endActivity`는 알림 취소 전에 `cancel`.
- [ ] **Step 4: 빌드와 테스트** — assemble + test 명령. Expected: BUILD SUCCESSFUL, 전체 PASS.
- [ ] **Step 5: 커밋** — `feat(android): freeze the countdown at zero when endAt passes`

### Task 5: 문서와 검증 항목

**Files:**
- Modify: `src/types.ts:19` (timer 주석)
- Modify: `README.md` (Status 문단, 플랫폼 차이표 `content.timer` 행과 그 아래 note의 timer 언급, 로드맵, 정확한 알람 안내 절)
- Modify: `CHANGELOG.md` (`[Unreleased]` → `### Added`)
- Modify: `docs/plans/2026-08-01-device-verification.md` (Android 절에 항목 추가)

- [ ] **Step 1: 문서 갱신** — 스펙 §6. README 표의 Android 칸은 남는 차이 세 가지((a) 권한 없으면 최대 약 10분 늦게 00:00, Doze 중 더 밀림 (b) API 23은 종료 시각 표시 (c) 표준 알림 템플릿)를 적는다. "unvalidated echo" 문구 삭제.
- [ ] **Step 2: 기기 검증 항목 추가** — Review Focus 1·2·3·5 각각 체크박스 하나씩, 그리고 카운트다운 표시 / 일시정지→재개 / 포그라운드 경로 00:00 전환 / 정확한 알람 권한 있을 때 정각 전환.
- [ ] **Step 3: 확인** — `npm run typecheck && npm test` (루트) 와 Android test 명령. Expected: 모두 통과.
- [ ] **Step 4: 커밋** — `docs: document the Android countdown and its remaining differences`
