# `feature/native-countdown` 정리 — 이식/폐기 판정

**작성:** 2026-07-27
**배경:** 같은 "네이티브 카운트다운" 기능이 두 브랜치로 갈라져 구현됨. 10MM 미션 타이머 계획서가 전제하는 `feat/mission-countdown`(이하 **B**, `978e176`)을 채택하기로 확정했고, `feature/native-countdown`(이하 **A**, 8커밋)에 남은 작업을 항목별로 판정한다.

## 계약 차이 (이식 가능 여부를 가르는 축)

| | A `feature/native-countdown` | B `feat/mission-countdown` (채택) |
|---|---|---|
| `LiveActivityTimer` | 판별 유니온: `running`→`endAt`, `paused`→`remainingSeconds`, `completed` | 평면: `{startAt, endAt, pauseAt?, state}` |
| `completionText` | 있음 (타이머 필드) | **없음** |
| `referenceId` / `getActiveActivities` | 없음 | 있음 |
| 일시정지 표현 | 남은 초를 JS가 계산해 고정값 전달 | `pauseAt` 타임스탬프 전달, 네이티브가 계산 |

A의 파싱·표현 로직은 대부분 유니온 shape에 결합돼 있어 그대로는 못 쓴다. 반대로 **빌드·패키징·입력검증·구조 개선은 계약과 무관**해서 그대로 이식 가능하다.

---

## 판정

### ✅ 그대로 이식 (계약 무관, 우선순위 높음)

**1. `android/.npmignore` (신규 2줄)**
```
.gradle/
build/
```
B에 없음. 앱이 `git+ssh://...#<sha>`로 라이브러리를 설치하는 현재 구조에서는 저장소 내용이 그대로 `node_modules`에 들어오므로, 빌드 산출물 제외가 실질 이득이다. 위험 0.

**2. `android/build.gradle` Kotlin 버전 해석**
A가 B보다 견고하다.

- A: `kotlinVersion` → `kotlin_version` → `'1.9.24'` 3단 폴백, classpath와 stdlib **양쪽**에 적용
- B: `kotlinVersion`만 확인, `kotlin_version` 폴백 없음, `ext.kotlin_version` 재할당 방식 유지

RN 0.86 앱은 `kotlinVersion = 2.1.20`을 rootProject에 넣으므로 둘 다 동작하지만, `kotlin_version`만 정의하는 구형 소비자에서 A만 살아남는다. **A 버전으로 교체 권장.**

### ⚠️ 부분 이식 (핵심만 뽑아내기)

**3. `ios/LiveActivityTimerParser.swift` (+135) — 대부분 폐기, 두 가지만 이식**

(a) **CFBoolean 가드 — 실제 버그 수정**
```swift
guard let number = rawValue as? NSNumber,
      CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
```
B는 `timer["startAt"] as? NSNumber`로만 검사한다. RN 브리지에서 JS `true`는 CFBoolean 기반 `NSNumber`로 넘어오므로 이 캐스트가 **성공**하고 `.doubleValue == 1.0`, `.isFinite == true`를 통과한다. 즉 B는 `{startAt: true, endAt: <유효값>, state: 'running'}`을 받아들여 1970년 기준 타이머를 만든다. 심각도는 낮지만 실재하는 구멍.

(b) **테스트 가능한 구조**
A는 파싱을 독립 `enum` + `throws` + 타입화된 에러로 분리했다. B는 모듈 내부 `private static func` + `RCTPromiseRejectBlock` 직접 호출이라 **유닛테스트가 원천적으로 불가능**하다. 파싱을 rejecter에서 떼어내고 호출부에서 에러→reject로 변환하는 패턴만 가져온다.

그 외 유니온 분기 로직은 폐기.

**4. Widget 렌더링 (+101) — B가 이미 보유, 3개 항목만 이식**

B에도 자체 위젯 구현이 있고 `context.isStale` 처리도 들어 있다. A가 나은 점만 뽑는다.

- **표현 로직 분리**: A는 `LiveActivityTimerPresentation`(`countdown`/`fixed`/`completion`/`hidden` 4상태 + `format(seconds:)`)을 별도 파일로 두고 위젯 타겟에 공유 컴파일한다. B는 뷰 안에 인라인이라 검증 불가.
- **minimal 영역 분기**: A는 실행중 `timer` / 일시정지 `pause.fill` / 완료 `checkmark` 아이콘. B는 벨 아이콘 고정.
- **`completionText` 필드**: 아래 6번 참조.

**5. `example/ios/scripts/add_widget_target.rb` (+18) — 경로 수정분만 이식**

두 성격이 섞여 있다.

- 공유 소스 참조 경로 `../../ios/` → `../../../ios/`, `Info.plist` 참조를 `"#{WIDGET}/Info.plist"` → `'Info.plist'`로 수정, `ref.path` 재지정 제거 → **버그 수정으로 보이며 이식 대상**
- `LiveActivityTimerPresentation.swift`를 공유 소스에 추가 → 4번 이식 시 함께

이식 전 B 기준으로 위젯 타겟 생성이 실제로 되는지 먼저 재현할 것. (경로 수정이 정말 필요한지 확인 필요 — A 쪽 디렉터리 구조 변경에 딸린 것일 수 있음)

### ❌ 폐기

**6. `tests/ios/*Tests.swift` (+195) — 지금 실행되지 않는 파일**

`LiveActivityTimerParserTests.swift`(110줄) + `LiveActivityTimerPresentationTests.swift`(85줄)이 있지만:

- `example/ios/LiveActivityExample.xcodeproj/project.pbxproj`에 참조 **0건**
- `package.json`에 실행 스크립트 없음 (`scripts`는 `typecheck`뿐)

즉 어떤 타겟에도 연결돼 있지 않아 한 번도 실행된 적이 없다. 입력 shape도 A 계약 전용이라 그대로는 못 쓴다.

**폐기하되 두 가지를 건진다:**
- 테스트 케이스 목록은 B용 Swift 테스트를 쓸 때 명세로 참고 (경계값, 불리언 입력, 음수, 비정수 등)
- 더 중요한 사실: **이 라이브러리에 Swift 테스트 타겟이 아예 없다** → 별도 과제로 분리

**공백은 Kotlin 쪽도 같다 (2026-08-01 확인).** 계획서 Task 10 교차 검증에서
`./gradlew :react-native-live-activity:testDebugUnitTest`가 **`NO-SOURCE`**로 끝났다.
`assembleDebug`는 BUILD SUCCESSFUL이지만 Android 유닛 테스트는 0건이다.
즉 이 라이브러리는 **JS 테스트(example jest 10건)만 있고 네이티브 양쪽 모두 검증 공백**이다.
"테스트 타겟 신설"을 Swift 전용이 아니라 Swift + Kotlin 과제로 잡을 것.

**7. `README.md` / `PRD.md` (+121)**

전부 A 계약 기준 예제(`timer: { state: 'running', endAt, completionText }`)라 B에 그대로 넣으면 오문서화가 된다. **폐기하고 B 계약으로 재작성.** 문서 구성(개요 → 카운트다운 시작 → 일시정지 → 완료)은 그대로 따라도 좋다. `TECH-PLAN.md` 참조는 유효하다(파일 존재 확인함).

---

## 별도 발견: `"10분 달성!"`이 라이브러리에 하드코딩

범용 라이브러리에 10MM 전용 문구가 박혀 있다. 양쪽 다 해당된다.

- A: `LiveActivityTimerParser.defaultCompletionText = "10분 달성!"` — **라이브러리 코드**
- B: 예제 위젯의 `Text(compact ? "달성!" : "10분 달성!")` — 예제라 상대적으로 나음

B에는 `completionText` 개념 자체가 없어서(타입 정의에 0건) 앱이 완료 문구를 넘길 방법이 없다. 계획서 Task 7에서 10MM 전용 Widget Extension을 만들 때 이 문자열을 앱이 소유하도록 해야 한다.

**→ A의 `completionText` 필드를 B의 평면 타이머 shape에 추가하는 것을 권장.** 계약 변경이므로 앱 pin SHA 갱신이 필요하다. Task 7 착수 전에 하면 비용이 가장 적다.

---

## 이식 순서 (제안)

앱이 `978e176`을 SHA로 고정해 쓰고 있으므로, 계약을 바꾸는 항목은 앱 pin 갱신을 동반한다. 계약 무관 항목부터 처리해 pin 갱신 횟수를 줄인다.

| 순서 | 항목 | 계약 변경 | 앱 pin 갱신 |
|---|---|---|---|
| 1 | `.npmignore` 이식 | 없음 | 불필요 |
| 2 | `build.gradle` Kotlin 폴백 이식 | 없음 | 불필요 |
| 3 | CFBoolean 가드 + 파서 구조 분리 | 없음 | 불필요 |
| 4 | Swift 테스트 타겟 신설 + 3번 검증 | 없음 | 불필요 |
| 5 | `completionText` 필드 추가 | **있음** | 필요 |
| 6 | 표현 로직 분리 + minimal 아이콘 + 위젯 스크립트 | 없음 | 불필요 |
| 7 | README/PRD를 B 계약으로 재작성 | 없음 | 불필요 |
| 8 | `feature/native-countdown` 브랜치 삭제 (로컬+origin) | — | — |

1~4는 한 번에 묶어도 되고, 5는 계획서 Task 7 착수 전에 끝내는 게 좋다.

## 남는 질문

- `add_widget_target.rb`의 경로 수정이 B 기준에서도 필요한지 (A의 디렉터리 변경에 딸린 것일 가능성)
- 두 클론의 `main` 갈라짐(externalProjects +3, base 클론 +1, 둘 다 미푸시) 정리 — 이식 작업을 어느 클론에서 할지 먼저 정해야 함
