import Foundation

/// JS에서 넘어온 content 페이로드의 파싱·검증.
///
/// React / ActivityKit을 의도적으로 import 하지 않는다. 그래야 `tests/ios`의
/// SwiftPM 테스트 타겟에 그대로 컴파일해 검증할 수 있다. `LiveActivityModule`이
/// 여기서 던진 에러를 `RCTPromiseRejectBlock` 호출로 변환한다.
public enum LiveActivityContentError: Error, Equatable {
  case invalidTitle
  case invalidProgress
  case timerNotAnObject
  case timerBoundsInvalid
  case timerEndBeforeStart
  case timerStateInvalid
  case timerPauseMissing
  case timerPauseOutOfRange

  /// reject 메시지. 기존 구현이 쓰던 문자열을 그대로 유지한다.
  public var message: String {
    switch self {
    case .invalidTitle:
      return "content.title must be a non-empty string"
    case .invalidProgress:
      return "content.progress must be a number between 0 and 1"
    case .timerNotAnObject:
      return "content.timer must be an object"
    case .timerBoundsInvalid:
      return "content.timer startAt and endAt must be finite numbers"
    case .timerEndBeforeStart:
      return "content.timer endAt must be after startAt"
    case .timerStateInvalid:
      return "content.timer state is invalid"
    case .timerPauseMissing:
      return "content.timer pauseAt is required when paused"
    case .timerPauseOutOfRange:
      return "content.timer pauseAt must be within the timer interval"
    }
  }
}

public struct LiveActivityParsedTimer: Equatable {
  public let startAt: Date
  public let endAt: Date
  public let pauseAt: Date?
  public let state: String

  public init(startAt: Date, endAt: Date, pauseAt: Date?, state: String) {
    self.startAt = startAt
    self.endAt = endAt
    self.pauseAt = pauseAt
    self.state = state
  }
}

public struct LiveActivityParsedContent: Equatable {
  public let title: String
  public let subtitle: String?
  public let progress: Double?
  public let timer: LiveActivityParsedTimer?

  public init(title: String, subtitle: String?, progress: Double?, timer: LiveActivityParsedTimer?) {
    self.title = title
    self.subtitle = subtitle
    self.progress = progress
    self.timer = timer
  }
}

public enum LiveActivityContentParser {

  public static let validTimerStates: Set<String> = ["running", "paused", "completed"]

  public static func parse(_ content: [AnyHashable: Any]) throws -> LiveActivityParsedContent {
    guard let title = content["title"] as? String, !title.isEmpty else {
      throw LiveActivityContentError.invalidTitle
    }

    let subtitle = content["subtitle"] as? String

    var progress: Double?
    if let rawProgress = content["progress"], !(rawProgress is NSNull) {
      guard let value = finiteDouble(rawProgress), value >= 0, value <= 1 else {
        throw LiveActivityContentError.invalidProgress
      }
      progress = value
    }

    var timer: LiveActivityParsedTimer?
    if let rawTimer = content["timer"], !(rawTimer is NSNull) {
      timer = try parseTimer(rawTimer)
    }

    return LiveActivityParsedContent(
      title: title,
      subtitle: subtitle,
      progress: progress,
      timer: timer
    )
  }

  private static func parseTimer(_ value: Any) throws -> LiveActivityParsedTimer {
    guard let timer = value as? [AnyHashable: Any] else {
      throw LiveActivityContentError.timerNotAnObject
    }

    guard
      let startMilliseconds = finiteDouble(timer["startAt"]),
      let endMilliseconds = finiteDouble(timer["endAt"])
    else {
      throw LiveActivityContentError.timerBoundsInvalid
    }

    let startAt = date(fromMilliseconds: startMilliseconds)
    let endAt = date(fromMilliseconds: endMilliseconds)
    guard endAt >= startAt else {
      throw LiveActivityContentError.timerEndBeforeStart
    }

    guard
      let state = timer["state"] as? String,
      validTimerStates.contains(state)
    else {
      throw LiveActivityContentError.timerStateInvalid
    }

    var pauseAt: Date?
    if let rawPause = timer["pauseAt"], !(rawPause is NSNull) {
      guard let pauseMilliseconds = finiteDouble(rawPause) else {
        throw LiveActivityContentError.timerBoundsInvalid
      }
      pauseAt = date(fromMilliseconds: pauseMilliseconds)
    }

    if state == "paused", pauseAt == nil {
      throw LiveActivityContentError.timerPauseMissing
    }
    if let pauseAt, !(startAt...endAt).contains(pauseAt) {
      throw LiveActivityContentError.timerPauseOutOfRange
    }

    return LiveActivityParsedTimer(
      startAt: startAt,
      endAt: endAt,
      pauseAt: pauseAt,
      state: state
    )
  }

  /// 숫자로 쓸 수 있는 값만 통과시킨다.
  ///
  /// RN 브리지에서 JS `true`/`false`는 CFBoolean 기반 `NSNumber`로 넘어와
  /// `as? NSNumber` 캐스트에 성공해 버린다. CFTypeID로 걸러내야 한다.
  /// NaN·무한대도 여기서 막는다.
  static func finiteDouble(_ raw: Any?) -> Double? {
    guard let number = raw as? NSNumber else { return nil }
    guard CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
    let value = number.doubleValue
    guard value.isFinite else { return nil }
    return value
  }

  static func date(fromMilliseconds value: Double) -> Date {
    Date(timeIntervalSince1970: value / 1_000)
  }

  public static func milliseconds(from date: Date) -> Double {
    date.timeIntervalSince1970 * 1_000
  }
}
