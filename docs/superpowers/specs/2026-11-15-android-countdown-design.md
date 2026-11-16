# Android 네이티브 카운트다운 설계

**작성:** 2026-11-15
**상태:** 승인 대기 (리뷰어 검토 반영 완료)

## 의도와 이유

v0.1.0에서 `content.timer`는 iOS만 읽는다. Android는 이 필드를 파싱도, 검증도,
렌더링도 하지 않는다 (README "Platform behavior differences" 표의 `content.timer` 행).
목표는 **iOS와 Android의 차이를 줄이는 것**이다. 같은 timer payload가 Android에서도
같은 규칙으로 검증되고, JS가 매초 update하지 않아도 알림에서 남은 시간이 줄어들며,
`endAt`이 지나면 iOS처럼 0에서 멈춰야 한다.

## 목표

1. Android가 iOS와 **같은 규칙, 같은 에러 메시지**로 `content.timer`를 검증한다.
   잘못된 timer는 `E_INVALID_CONTENT`(start) / `E_INVALID_ARGUMENT`(update)로 reject된다.
   이 코드 구분은 기존 Android 동작을 그대로 따른다.
2. `state: 'running'`이면 JS 호출 없이 OS가 알림에서 초 단위로 카운트다운한다.
3. `endAt`이 지나면 알림은 음수로 넘어가지 않고 **00:00 고정** 상태로 바뀐다.
   앱 프로세스가 죽어 있어도 마찬가지다.
4. `paused`는 `endAt - pauseAt`을 고정 텍스트로, `completed`는 00:00을 고정 텍스트로 보여준다.
5. 일반 알림 경로와 포그라운드 서비스 경로 모두 같은 결과를 낸다.

## 비목표

- Android 16 Live Updates / `Notification.ProgressStyle` / promoted ongoing notification.
  대상 기기 범위가 너무 좁다.
- 커스텀 `RemoteViews` 레이아웃. 표준 알림 템플릿만 쓴다.
- 프로세스 재시작 후 `getActiveActivities()` 복원. 지금처럼 빈 배열을 돌려주고,
  그 차이는 README 표에 남긴다.
- iOS 쪽 변경. iOS 파서와 위젯은 손대지 않는다.

## 안티골 (동작은 해도 실패로 보는 것)

- 라이브러리가 `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`을 매니페스트에 **강제로 선언**하는 것.
  Play 정책상 정당화가 필요한 권한이라, 모든 소비자 앱에 떠넘기면 안 된다.
- 종료 알람이 **이미 사라진 알림을 다시 띄우는 것**. 사용자가 지웠거나, End됐거나,
  포그라운드 서비스가 죽은 경우가 해당된다.
- iOS와 다른 검증 규칙이 생기는 것. 예를 들어 Android만 `endAt == startAt`을 거부하는 경우.

## 제약

- `minSdkVersion` 23, `compileSdkVersion` 34(소비자 값 우선).
- 알림 카운트다운(`Notification.Builder.setChronometerCountDown`)은 **API 24+**.
- 카운트다운 크로노미터는 `when`에 도달해도 멈추지 않고 음수로 계속 간다. 그래서
  종료 처리는 별도 장치가 필요하다.
- Android 12+(API 31)에서 정확한 알람은 `AlarmManager.canScheduleExactAlarms()`가
  true일 때만 쓸 수 있다. API 23–30은 `setExactAndAllowWhileIdle`에 권한이 필요 없다.
- 유닛테스트는 순수 JVM(`junit:junit:4.13.2`, `JavaOnlyMap`)이다. Robolectric은 없다.
  Android 프레임워크에 의존하는 부분(알람, Receiver, 알림 게시)은 실기기 검증 항목으로 넘긴다.

## 접근 방식 (검토한 후보)

| | 방식 | 트레이드오프 | 판정 |
|---|---|---|---|
| **A** | 알림 크로노미터 + 다음 전환 시각에 알람 → Receiver가 알림을 다시 게시 | Receiver와 알람 관리 코드가 늘어난다. 정확한 알람 권한이 없으면 Doze 중 몇 분 늦을 수 있다 | **채택** |
| B | 크로노미터 + `setTimeoutAfter` | 가장 짧다. 하지만 `endAt`에 알림이 사라져서 iOS(종료 상태로 남음)와 달라진다 | 기각 |
| C | 크로노미터만 쓰고 음수 진행은 문서화 | 가장 단순하다. 하지만 플랫폼 차이를 하나 더 늘린다 | 기각 |

소유자가 A를 선택했다 (2026-11-15). 목적이 플랫폼 차이를 줄이는 것이기 때문이다.

## 설계

### 1. 파싱 — `LiveActivityContentParser.kt`

`ParsedContent`에 `timer: ParsedTimer?`를 추가한다.

```kotlin
enum class TimerState { RUNNING, PAUSED, COMPLETED }
data class ParsedTimer(val startAtMs: Long, val endAtMs: Long, val pauseAtMs: Long?, val state: TimerState)
```

검증 순서와 메시지는 `ios/LiveActivityContentParser.swift`의 `parseTimer`와
**한 글자도 다르지 않게** 맞춘다.

| 조건 | 메시지 |
|---|---|
| `timer`가 Map이 아님 (`null`/키 없음은 timer 없음으로 취급) | `content.timer must be an object` |
| `startAt`/`endAt`이 Number가 아니거나 유한하지 않음 | `content.timer startAt and endAt must be finite numbers` |
| `endAt < startAt` (**같으면 통과**. iOS가 `>=`이므로) | `content.timer endAt must be after startAt` |
| `state`가 String이 아니거나 세 값 중 하나가 아님 | `content.timer state is invalid` |
| `pauseAt`이 있는데 Number가 아니거나 유한하지 않음 | `content.timer startAt and endAt must be finite numbers` (iOS와 동일) |
| `state == paused`인데 `pauseAt` 없음 | `content.timer pauseAt is required when paused` |
| `pauseAt`이 `startAt..endAt` 밖 (`state`와 무관하게 검사) | `content.timer pauseAt must be within the timer interval` |

Boolean은 `ReadableType.Number`가 아니므로 자연히 걸러진다. NaN/Infinity는
`isFinite()`로 명시적으로 막는다. **모든 비교는 원본 `Double`로 한 뒤**, 검증을 통과한
값만 `toLong()`으로 내려 저장한다(소수 ms는 버림). 먼저 내리면 `startAt=100.7, endAt=100.3`
같은 입력을 iOS는 거부하는데 Android는 통과시키게 된다. (리뷰 반영, 내 판단)

기존 title/progress 메시지는 바꾸지 않는다. 두 플랫폼의 기존 메시지가 이미 달라서
맞추면 동작이 바뀌기 때문이다. 이번 범위는 timer만이다.

### 2. 표현 결정 — 새 파일 `LiveActivityTimerPresentation.kt` (순수 Kotlin)

시각과 SDK 버전을 인자로 받는 순수 함수다. JVM에서 테스트할 수 있다.

```kotlin
sealed class TimerDisplay {
  data class Countdown(val untilMs: Long) : TimerDisplay()   // 크로노미터, API 24+
  data class Fixed(val text: String) : TimerDisplay()        // 고정 텍스트
  data class EndsAt(val atMs: Long) : TimerDisplay()         // API 23 전용: 종료 시각 표시
}
data class TimerPresentation(val display: TimerDisplay, val nextTransitionAtMs: Long?)

fun present(timer: ParsedTimer, nowMs: Long, sdkInt: Int): TimerPresentation
```

| 상태 / 시점 | API 24+ display | API 23 display | nextTransitionAtMs |
|---|---|---|---|
| running, `now < startAt` | `Fixed(format(endAt - startAt))` | 같음 | `startAt` |
| running, `startAt <= now < endAt` | `Countdown(endAt)` | `EndsAt(endAt)` | `endAt` |
| running, `now >= endAt` | `Fixed("00:00")` | 같음 | `null` |
| paused | `Fixed(format(endAt - pauseAt))` | 같음 | `null` |
| completed | `Fixed("00:00")` | 같음 | `null` |

`format(ms)`: 초 단위로 내림. 알림 크로노미터가 쓰는 `DateUtils.formatElapsedTime`과
같은 형식이다. 1시간 미만이면 `mm:ss`(`05:00`, `00:59`, `00:00`), 1시간 이상이면 `h:mm:ss`.
이렇게 해야 카운트다운에서 고정 텍스트로 바뀔 때 같은 자리의 모양이 튀지 않는다.
iOS 쪽 모양은 소비자 위젯이 정하는 것이므로, Android에서는 같은 알림 안에서의
일관성을 우선한다. 구현은 `formatElapsedTime`을 직접 쓰지 않고 같은 형식을 순수
Kotlin으로 만든다(JVM 테스트 가능하게). (리뷰 반영, 내 판단)

`now < startAt` 행은 iOS `Text(timerInterval:)`이 시작 전에 전체 길이를 보여주는
동작에 맞춘 것이다 (빌더 판단).

### 3. 알림 빌드 — `LiveActivityNotifications.build()`

시그니처에 `timer: TimerDisplay?`를 추가한다. timer가 없으면 지금과 완전히 같은 알림이다.

- `Countdown(untilMs)`: `setShowWhen(true)`, `setWhen(untilMs)`, `setUsesChronometer(true)`,
  `setChronometerCountDown(true)`.
- `Fixed(text)`: `setSubText(text)`. 헤더 줄에 들어가서 크로노미터와 같은 위치다.
  `setShowWhen(false)`는 유지한다.
- `EndsAt(atMs)`: `setShowWhen(true)`, `setWhen(atMs)`. 크로노미터가 아니라 시스템이
  시각("오후 3:45")으로 표시한다.

이미 쓰고 있는 `setOnlyAlertOnce(true)`가 있어서 재게시해도 소리나 진동이 다시 나지 않는다.

### 4. 전환 알람 — 새 파일 `LiveActivityTimerScheduler.kt` + `LiveActivityTimerReceiver.kt`

**스케줄러** (`internal object`)

- `schedule(context, activityId, payload, atMs)`: 브로드캐스트 `PendingIntent`를
  `requestCode = LiveActivityNotifications.id(activityId)`,
  `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`로 만든다. 같은 id면 기존 알람을 덮어쓴다.
  - API < 31, 또는 `canScheduleExactAlarms()`가 true면 `setExactAndAllowWhileIdle(RTC_WAKEUP, atMs, pi)`.
  - 그 외(API 31+이고 권한 없음)에는 `setWindow(RTC_WAKEUP, atMs, 10분, pi)`.
    Android 12+가 허용하는 최소 window가 10분이라 최대 약 10분 늦는다.
  - `SecurityException`이 나면 `setWindow`로 다시 시도한다.
  - `setAndAllowWhileIdle`은 쓰지 않는다. 부정확한 알람은 남은 시간의 큰 비율(최대 약 75%)까지
    밀릴 수 있어서, 1시간 타이머가 수십 분 늦게 00:00이 될 수 있다. `setWindow`는 Doze 중에는
    밀리지만, 사용자가 화면을 켜면 Doze가 풀리며 바로 실행되므로 "사용자가 알림을 보는
    시점" 기준으로는 약 10분 이내다. (소유자 결정, 2026-11-15)
- `cancel(context, activityId)`: 같은 `PendingIntent`로 `AlarmManager.cancel` 후 `pi.cancel()`.

**페이로드** (Intent extras): `activityId`, `title`, `subtitle`, `progress`(-1 = 없음),
`startAtMs`, `endAtMs`, `pauseAtMs`(-1 = 없음), `state`(enum name), `foreground`(Boolean).
Receiver는 이것만으로 알림을 처음부터 다시 만든다. 프로세스가 죽어 있어도 동작한다.

**Receiver** (`exported="false"`, 매니페스트 등록)

1. extras로 `ParsedTimer`를 복원하고 `present(timer, System.currentTimeMillis(), SDK_INT)`를 호출한다.
2. **알림이 아직 떠 있는지 확인한다** (`NotificationManager.activeNotifications`, API 23+).
   - 일반 경로: tag = `LiveActivityNotifications.tag(id)`, id = `LiveActivityNotifications.id(id)`인 항목.
   - 포그라운드 경로: 서비스는 `startForeground(id(activityId))`로 tag 없이 게시하므로
     tag == null && id 일치인 항목.
   - 없으면 아무것도 하지 않고 끝낸다 (안티골: 사라진 알림 부활 금지).
3. 있으면 같은 tag/id로 `notify()`해서 덮어쓴다. 포그라운드 경로도 서비스를 다시
   시작하지 않고 `notify(id)`만 한다. 포그라운드 알림의 내용을 바꾸는 표준 방법이고,
   Android 12+의 백그라운드 FGS 시작 제한을 건드리지 않는다.
4. 결과의 `nextTransitionAtMs`가 있으면(시작 전 → 시작) 같은 페이로드로 다시 예약한다.

### 5. 모듈 연결 — `LiveActivityModule.kt`, `LiveActivityForegroundService.kt`

- start/update에서 파싱 후 `present(...)`를 계산해 `TimerDisplay`를 알림 빌드에 넘긴다.
  포그라운드 경로는 서비스 Intent에 timer extras를 추가로 실어 보내고, 서비스가
  `present`를 다시 계산한다(서비스가 늦게 실행돼도 그 시점 기준이 되도록).
- **알림을 게시하는 쪽이 알람도 정한다.** 일반 경로는 모듈이, 포그라운드 경로는
  서비스가 자기가 계산한 `present` 결과로 `nextTransitionAtMs != null`이면 `schedule`,
  아니면 `cancel`한다. timer가 없는 update도 `cancel`한다. 모듈이 미리 정하면 서비스가
  늦게 실행됐을 때 판단이 어긋날 수 있다. (리뷰 반영, 내 판단)
- `endActivity`: 알림을 취소하기 **전에** `cancel`한다.
- 스케줄링 실패는 start/update를 실패시키지 않는다. 알림은 이미 게시됐고, 최악의 경우
  iOS와 다르게 음수로 넘어갈 뿐이다. 로그만 남긴다.
- `getActiveActivities()`의 content 에코는 지금처럼 JS가 보낸 원본 그대로다. 이제
  검증을 통과한 값만 저장되므로 README의 "unvalidated echo" 주석을 삭제한다.
  알람이 00:00으로 바꿔도 스냅샷의 `state`는 `running`으로 남는다. iOS도 stale 이후
  ActivityKit content가 바뀌지 않으므로 같은 동작이다.

### 6. 문서

- `src/types.ts`: `timer` 주석을 "iOS only"에서 양쪽 지원으로 바꾸고, Android 표시 방식을 한 줄로 적는다.
- README:
  - Status 문단과 로드맵(`Android native countdown timer` 체크)을 갱신한다.
  - 플랫폼 차이표의 `content.timer` 행을 바꾼다. 남는 차이는 세 가지다.
    (a) 정확한 알람 권한이 없으면(Android 14+에서는 기본값) 00:00 전환이 최대 약 10분 늦을 수 있고,
        그동안 음수가 보임. 화면이 꺼진 Doze 중에는 더 밀리지만 화면을 켜면 곧 반영됨.
    (b) API 23은 카운트다운 대신 종료 시각 표시.
    (c) 렌더링은 라이브러리가 정한 표준 알림 템플릿.
  - 정확한 시각이 필요한 앱은 `USE_EXACT_ALARM`(타이머·알람 앱, Play 정책 대상) 또는
    `SCHEDULE_EXACT_ALARM`(사용자가 설정에서 허용)을 직접 선언하라고 안내한다. 권한이 있으면
    라이브러리가 자동으로 정확한 알람을 쓴다. (소유자 결정, 2026-11-15)
- CHANGELOG `[Unreleased]`에 Added로 기록하고, 0.1.0 Known limitations의 해당 항목은
  다음 릴리즈 노트에서 해소됐다고 적는다.
- `docs/plans/2026-08-01-device-verification.md`의 Android 절에 이 기능의 실기기 항목을 추가한다.
  카운트다운, 일시정지/재개, 앱 종료 후 00:00 전환, 사용자가 지운 알림이 다시 안 뜨는지,
  포그라운드 경로, API 23 동작(가능하면).
- example 앱(`example/App.tsx`)은 이미 timer를 보낸다면 Android에서도 그대로 쓰고,
  아니면 Android에서 timer 버튼이 동작하도록 손본다 (빌더 판단).

## 테스트

- `LiveActivityContentParserTest`: 위 표의 각 거부 사례, 경계(`endAt == startAt` 통과,
  `pauseAt == startAt`/`== endAt` 통과), Boolean/NaN 거부, timer 없음/`null`.
- `LiveActivityTimerPresentationTest`: 위 표의 모든 행 × API 23/24, `format`의
  경계(59.9초 → `00:59`, 3600초 → `1:00:00`).
- 스케줄러, Receiver, 알림 게시: JVM 테스트 대상이 아니다. 실기기 검증 문서 항목으로 다룬다.
- CI에서 기존 `./gradlew test`(Android 네이티브 유닛테스트)가 통과해야 한다.

## 빌더에게 맡기는 것

- 파일과 클래스 이름, extras 키 이름.
- example 앱 수정 범위.
- 로그 태그와 형식.
