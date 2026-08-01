# v0.1.0 실기기 검증 기록

이 라이브러리의 가치는 전부 네이티브 동작에 있는데, 실기기 검증 이력이 한 번도 없다.
PRD §10 성공지표 두 개("Example app can start/update/end a live surface on
supported iOS and Android devices", real-device E2E)가 이 문서의 완료 여부에
달려 있다. 시뮬레이터로는 Dynamic Island, 포그라운드 서비스 회수 동작,
실제 알림 권한 프롬프트를 제대로 볼 수 없으므로 반드시 실기기가 필요하다.

**이 문서는 사람이 실기기에서 직접 채운다.** 에이전트는 대행할 수 없다.

---

## 검증 기록

**검증자:**
**날짜:**
**라이브러리 커밋:** (검증 시작 전 `git rev-parse HEAD`로 확인해 채울 것 — 이 문서를
처음 만든 시점의 HEAD는 `a84f418`이지만, 실제 검증은 그보다 나중 커밋에서
수행될 수 있다. 아래 판정은 **이 커밋에 대해서만** 유효하다.)

## 환경

| 항목 | 값 |
|---|---|
| iOS 기기 / OS |  |
| Android 기기 / OS |  |
| 라이브러리 커밋 (위와 동일한 값) |  |

## 사전 준비

`.worktrees/`가 남아있으면 example 빌드가 autolink 무한재귀로 죽는 문제가
과거에 있었으나 해결되었다 — 루트에 `.worktrees/`가 없는 상태만 유지하면 된다
(`git worktree list`로 확인; main worktree 한 줄만 나와야 함).

패키지는 스코프가 붙은 `@woobottle/react-native-live-activity`다. CocoaPods
pod 이름은 여전히 `react-native-live-activity`(스코프 없음)인데, 이는 의도된
것이고 오류가 아니다 — `pod install` 로그에 `react-native-live-activity`로
나와도 정상이다.

```sh
cd example
npm install
cd ios && pod install && cd ..

npm run ios     # 실기기 선택
npm run android # 실기기 선택
```

Android 쪽에서, 이 라이브러리의 Gradle 프로젝트 경로는 RN 오토링킹이 스코프된
npm 패키지 이름으로부터 유도하기 때문에 `:woobottle_react-native-live-activity`
이다 (예전 `:react-native-live-activity`가 아님). 빌드 로그나 Gradle 태스크를
직접 지정할 일이 있으면 이 경로를 써야 한다. 예:

```sh
cd example/android && ./gradlew :woobottle_react-native-live-activity:testDebugUnitTest
```

## iOS Widget Extension 셋업 — README 텍스트만으로 재현

**목적:** README의 "iOS Widget Extension setup" 절이 실제로 동작하는지 확인한다.
컨슈머가 실제로 밟게 될 통합 경로이고, 지금까지 한 번도 검증된 적 없다.

- [ ] `example/`을 참고하지 **말고**(=`example/ios/LiveActivityWidget` 타겟을
      복사/재사용하지 않고) 새 바닐라 RN 앱 하나(또는 `example`의 사본에서
      기존 Widget Extension 타겟을 지운 상태)에서, README "iOS Widget
      Extension setup"의 1~4단계만 보고 그대로 따라간다:
      1. Xcode에서 Widget Extension 타겟 추가 (Include Live Activity 체크,
         배포 타겟 16.1+)
      2. 이 라이브러리의 `ios/LiveActivityAttributes.swift`를 위젯 타겟
         멤버십에 추가 (복사본을 새로 만들지 않고)
      3. `ActivityConfiguration(for: LiveActivityAttributes.self)` +
         `dynamicIsland`를 직접 구현 (얼마나 최소한으로 구현해도 렌더링만
         되면 됨 — `example`의 `LiveActivityWidgetLiveActivity.swift`는
         "완전한 참고 구현"일 뿐 베껴 쓰는 대상이 아님)
      4. 앱 타겟 `Info.plist`에 `NSSupportsLiveActivities = YES` 추가
- [ ] 4단계까지 마친 뒤 위젯 타겟이 빌드된다
- [ ] `startActivity` 호출 시 방금 만든 위젯이 잠금화면에 실제로 렌더링된다
      (타입 불일치로 조용히 안 뜨는 실패 모드가 README에 명시돼 있음 — 그
      실패가 재현되지 않는지도 확인)
- [ ] (선택) `example/ios/scripts/add_widget_target.rb`로 동일한 타겟을
      스크립트로 재현해봐도 같은 결과가 나오는지 — README가 "선호하면"이라고
      말하는 대안 경로이므로 필수는 아님

## iOS 기능 검증 (16.1+ 실기기 필수)

> 근거: `ios/LiveActivityModule.swift`, `ios/LiveActivityContentParser.swift`를
> 읽고 도출한 기대 동작. 실제 실행으로 확인된 적은 없다.

- [ ] 앱 실행 → `isSupported()`가 true (`ActivityAuthorizationInfo().areActivitiesEnabled`)
- [ ] Start → 잠금화면에 Live Activity 표시
- [ ] Dynamic Island 지원 기기: compact/expanded/minimal 세 표현이 모두 정상
- [ ] Update → 제목·부제·진행률이 제자리에서 갱신 (새 활동이 생기지 않음 — `activity.update`)
- [ ] 카운트다운 시작(`timer: { state: 'running' }`) → 위젯의 남은 시간이
      **JS 호출 없이** 초 단위로 줄어듦 (`Text(timerInterval:)` 기반 렌더링)
- [ ] 일시정지(`timer: { state: 'paused', pauseAt }`) → 시간이 멈추고 그 값이 유지됨
- [ ] 재개(`state: 'running'`로 다시 update) → 멈춘 지점부터 이어서 감소
- [ ] 완료 상태(`state: 'completed'`) → 완료 표현으로 전환
- [ ] `pauseAt`이 `startAt..endAt` 범위 밖이거나 `paused`인데 `pauseAt`이
      없는 잘못된 timer payload → `E_INVALID_CONTENT`로 즉시 reject (앱을
      죽이거나 활동을 깨진 상태로 만들지 않는지 확인 — 파서 로직상 기대되는
      동작이지 실기기에서 확인된 적은 없음)
- [ ] 앱 강제종료 후 재실행 → `getActiveActivities()`가 진행 중 활동을 반환
      (ActivityKit이 프로세스와 독립적으로 상태를 들고 있음)
- [ ] End → 활동이 즉시 사라짐 (`dismissalPolicy: .immediate`)
- [ ] Live Activities를 시스템 설정에서 끈 상태 → `isSupported()`가 false,
      `startActivity`가 `E_DISABLED`로 reject
- [ ] **[플랫폼 분기 확인 — iOS]** 존재하지 않는 `activityId`(예: 실제로
      `startActivity`가 반환한 적 없는 무작위 UUID)로 `updateActivity`와
      `endActivity`를 각각 호출 → **둘 다** `E_NOT_FOUND`로 즉시 reject된다
      (`LiveActivityModule.swift`의 `findActivity(id:)`가 nil을 반환하는
      경로). 아래 Android 섹션의 동일 항목과 **결과가 다른지** 대조할 것 —
      README "Platform behavior differences" 표의 첫 두 행이 실제로
      갈라지는지 확인하는 항목.
- [ ] `{ android: { foregroundService: true } }`를 iOS에서 `startActivity`에
      넘김 → 아무 옵션도 안 준 것과 동일하게 동작한다 (no-op). 에러도,
      다른 렌더링도 없어야 함 — `LiveActivityModule.swift` 69-70행 주석대로
      "옵션은 교차 플랫폼 브리지 시그니처 통일을 위해 받아들여지지만 iOS에는
      영향이 없다"가 실제로 그런지 확인.

## Android 기능 검증 (13 이상 1대 + 14 이상 1대 권장)

> 근거: `android/src/main/java/com/woobottle/liveactivity/*.kt`를 읽고 도출한
> 기대 동작. **네이티브 카운트다운 타이머는 v0.1.0에서 Android 미지원이다** —
> `android/src/` 전체에 "timer" 문자열이 0회 등장하고, Kotlin 파서
> (`LiveActivityContentParser.kt`)는 title/subtitle/progress만 읽는다. 이는
> 알려진 제약으로 v0.1.0을 이 상태로 출시하기로 결정되었다. 아래 목록에
> Android 카운트다운 "기능" 항목은 없다 — 대신 그 payload가 크래시 없이
> 조용히 무시되는지 확인하는 항목 하나만 "문서화된 제약 확인" 관점으로 둔다.

- [ ] `example` 앱에서 Start 버튼을 처음 눌렀을 때 `POST_NOTIFICATIONS` 권한
      요청이 뜸 — **앱 실행/콜드스타트 시점이 아니다.** `example/App.tsx`의
      `ensureAndroidNotificationPermission()`은 `handleStart()` 안에서만
      호출되므로(45-64행), 앱을 켜자마자 다이얼로그를 기다리면 아무 일도
      일어나지 않는다. Start를 누르기 전에는 권한 요청이 뜨지 않는 게 정상.
- [ ] 권한 거부 상태 → `isSupported()`가 false, `startActivity`가
      `E_NOTIFICATION_PERMISSION`으로 reject
- [ ] **[Minor — isSupported 이중 게이트]** `POST_NOTIFICATIONS` 런타임 권한은
      **허용**하되, 시스템 설정에서 앱 알림 자체를 끔(알림 토글 OFF) → 이
      상태에서도 `isSupported()`가 false를 반환해야 한다.
      `hasNotificationPermission()`(`LiveActivityModule.kt` 187-196행)이
      런타임 권한과 `areNotificationsEnabled()`(시스템 토글)를 **둘 다** 요구
      하기 때문 — 권한 거부 항목(바로 위)만으로는 이 경로가 검증되지 않는다.
- [ ] Start → 상시 알림 표시 (스와이프로 지워지지 않음 — `setOngoing(true)`)
- [ ] Update → 같은 알림이 제자리에서 갱신 (중복 알림 없음 — 동일
      tag/id로 `notify`)
- [ ] End → 알림 사라짐
- [ ] **[플랫폼 분기 확인 — Android]** 존재하지 않는 `activityId`(예:
      `startActivity`가 반환한 적 없는 무작위 UUID)로 `updateActivity` 호출
      → reject되지 않고 **그 id로 새 알림이 뜬다** (`requireValidActivityId`는
      공백 여부만 검사하고, `showNotification`이 존재 확인 없이 바로
      `notify()`를 호출하기 때문 — `LiveActivityModule.kt` 90-113행). 이어서
      같은 무작위 id로 `endActivity` 호출 → **에러 없이 조용히 resolve**된다
      (존재하지 않는 알림을 `cancel()`하는 건 no-op). 바로 위 iOS 섹션의
      동일 항목(둘 다 `E_NOT_FOUND`)과 정확히 반대로 갈리는지 대조할 것 —
      README 표의 첫 두 행이 실제로 이렇게 갈리는지 확인하는 항목.
- [ ] **[문서화된 제약 확인]** `content.timer`가 포함된 payload로 Start/Update
      → 정상적으로 알림이 뜨거나 갱신됨, 크래시나 reject 없이 **timer 필드가
      조용히 무시됨** (title/subtitle/progress만 반영). README "Platform
      behavior differences" 표의 해당 행이 실제로 이렇게 동작하는지 확인하는
      항목 — 새 기능 검증이 아니다.
- [ ] `{ android: { foregroundService: true } }`로 Start → 포그라운드 서비스
      알림으로 뜸. `startForeground`에 `FOREGROUND_SERVICE_TYPE_DATA_SYNC`
      타입 플래그가 붙는 것은 Android 10(API 29, Q)부터이지 Android
      14 전용이 아니다 (`LiveActivityForegroundService.kt` 57-63행) — 이
      항목은 그 타입 플래그 자체를 확인하고, Android 14 전용 매니페스트
      권한(`FOREGROUND_SERVICE_DATA_SYNC`)은 아래 별도 항목에서 확인한다.
- [ ] **[Important — 포그라운드 동시성 한도]** 포그라운드 활동 A를
      `{ android: { foregroundService: true } }`로 Start한 뒤, **A를 End하지
      않은 채로** 두 번째 포그라운드 활동 B를 같은 옵션으로 Start → A의
      알림이 사라지고 B의 알림으로 **교체**된다 (알림이 두 개 쌓이지 않고,
      크래시나 조용한 실패도 없어야 함). `LiveActivityForegroundService.kt`
      15-17행 문서 주석 "v1 scope: one primary foreground activity at a
      time — starting another foreground activity replaces the hosted
      notification"과 README 표 마지막 행이 실제로 그런지 확인하는 항목 —
      지금까지 어떤 항목도 두 번째 포그라운드 활동을 시작해본 적이 없었다.
- [ ] 포그라운드 모드에서 앱을 백그라운드로 보내고 10분 방치 → 알림 유지
- [ ] Android 14 기기에서 `dataSync` 타입으로 정상 기동
      (`FOREGROUND_SERVICE_DATA_SYNC` 거부 크래시 없음)
- [ ] 포그라운드 서비스로 시작한 활동 상태에서 앱 프로세스를 강제종료 후
      재실행 → `getActiveActivities()`는 빈 배열을 반환 (인메모리 스냅샷이라
      프로세스 재시작에 살아남지 않음 — README 표에 문서화된 대로인지 확인).
      알림 자체는 OS가 서비스를 `null` intent로 재시작하며 `onStartCommand`가
      즉시 `stopSelf()`를 호출해 사라지는 게 기대 동작 — 실제로 사라지는지도
      함께 관찰
- [ ] **[Minor — 평범한 알림의 재시작 생존]** 위 항목과 대조: 포그라운드
      서비스 **없이**(`foregroundService` 옵션 없이) 시작한 평범한 알림
      상태에서 앱 프로세스를 강제종료 후 재실행 → `getActiveActivities()`는
      마찬가지로 빈 배열을 반환하지만(인메모리 스냅샷이라), **알림 자체는
      시스템 알림으로 독립적으로 게시된 것이라 화면에 계속 남아있어야 한다**
      (README: "a plain notification ... is posted independently of the
      process and typically survives"). 포그라운드 케이스(바로 위)는 알림이
      사라지는 게 기대 동작이고, 이 평범한 케이스는 알림이 남는 게 기대
      동작 — 두 결과가 실제로 다른지 대조할 것.

## [배포 후 전용] 게시된 패키지 설치 경로 검증

> **이 항목은 v0.1.0이 npm에 게시된 뒤에만 수행 가능하다 (Task 10 이후).**
> 그 전까지는 체크할 수 없는 항목이므로 미완료 상태로 남겨두고 Task 10 진행을
> 막지 않는다. 배포 후 사람이 별도로 채운다.

- [ ] **(배포 후)** 완전히 새로운 bare RN 앱을 생성
- [ ] **(배포 후)** `npm install @woobottle/react-native-live-activity` (또는
      `yarn add`)로 게시된 패키지를 설치 — 로컬 `file:` 참조가 아니라 실제
      npm 레지스트리에서
- [ ] **(배포 후)** iOS: `cd ios && pod install` — pod 이름은
      `react-native-live-activity`(스코프 없음)로 정상 resolve 되는지 확인
- [ ] **(배포 후)** Android: 별도 설정 없이 오토링킹만으로 빌드 성공
      (Gradle 프로젝트 경로가 `:woobottle_react-native-live-activity`로
      잡히는지)
- [ ] **(배포 후)** import 경로가 `@woobottle/react-native-live-activity`로
      정상 동작 (README·타입 선언 모두 이 이름 기준)
- [ ] **(배포 후)** 최소한 하나의 lifecycle (start → update → end)이 실기기에서
      동작

## 발견 사항

| # | 심각도 | 내용 | 조치 |
|---|---|---|---|
|   |        |      |      |

## 판정

- [ ] v0.1.0 릴리즈 가능 (배포 후 전용 항목 제외 — 그건 Task 10 이후 별도 확인)
- [ ] 블로커 있음 — 위 표 참조
