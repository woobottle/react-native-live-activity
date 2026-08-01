import XCTest
@testable import LiveActivityCore

final class LiveActivityContentParserTests: XCTestCase {

  // MARK: - title

  func testParsesMinimalContent() throws {
    let parsed = try LiveActivityContentParser.parse(["title": "Delivery"])
    XCTAssertEqual(parsed.title, "Delivery")
    XCTAssertNil(parsed.subtitle)
    XCTAssertNil(parsed.progress)
    XCTAssertNil(parsed.timer)
  }

  func testRejectsMissingTitle() {
    XCTAssertThrowsError(try LiveActivityContentParser.parse([:])) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .invalidTitle)
    }
  }

  func testRejectsEmptyTitle() {
    XCTAssertThrowsError(try LiveActivityContentParser.parse(["title": ""])) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .invalidTitle)
    }
  }

  // MARK: - progress

  func testParsesProgressBounds() throws {
    XCTAssertEqual(try LiveActivityContentParser.parse(["title": "t", "progress": 0]).progress, 0)
    XCTAssertEqual(try LiveActivityContentParser.parse(["title": "t", "progress": 1]).progress, 1)
  }

  func testRejectsProgressOutOfRange() {
    XCTAssertThrowsError(try LiveActivityContentParser.parse(["title": "t", "progress": 1.5])) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .invalidProgress)
    }
    XCTAssertThrowsError(try LiveActivityContentParser.parse(["title": "t", "progress": -0.1])) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .invalidProgress)
    }
  }

  /// JS `true`는 RN 브리지에서 CFBoolean 기반 NSNumber로 넘어와
  /// `as? NSNumber` 캐스트에 성공하고 doubleValue == 1.0이 된다. 막아야 한다.
  func testRejectsBooleanProgress() {
    XCTAssertThrowsError(try LiveActivityContentParser.parse(["title": "t", "progress": true])) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .invalidProgress)
    }
  }

  func testRejectsNaNProgress() {
    XCTAssertThrowsError(try LiveActivityContentParser.parse(["title": "t", "progress": Double.nan])) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .invalidProgress)
    }
  }

  // MARK: - timer

  private func timerContent(_ timer: [String: Any]) -> [AnyHashable: Any] {
    ["title": "t", "timer": timer]
  }

  func testParsesRunningTimer() throws {
    let parsed = try LiveActivityContentParser.parse(
      timerContent(["startAt": 1_000_000, "endAt": 1_600_000, "state": "running"])
    )
    let timer = try XCTUnwrap(parsed.timer)
    XCTAssertEqual(timer.startAt, Date(timeIntervalSince1970: 1_000))
    XCTAssertEqual(timer.endAt, Date(timeIntervalSince1970: 1_600))
    XCTAssertNil(timer.pauseAt)
    XCTAssertEqual(timer.state, "running")
  }

  func testParsesPausedTimer() throws {
    let parsed = try LiveActivityContentParser.parse(
      timerContent([
        "startAt": 1_000_000, "endAt": 1_600_000,
        "pauseAt": 1_300_000, "state": "paused",
      ])
    )
    XCTAssertEqual(try XCTUnwrap(parsed.timer).pauseAt, Date(timeIntervalSince1970: 1_300))
  }

  func testRejectsTimerThatIsNotAnObject() {
    XCTAssertThrowsError(
      try LiveActivityContentParser.parse(["title": "t", "timer": "nope"])
    ) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .timerNotAnObject)
    }
  }

  /// `{startAt: true}`가 1970년 기준 타이머로 통과하던 구멍.
  func testRejectsBooleanTimerBounds() {
    XCTAssertThrowsError(
      try LiveActivityContentParser.parse(
        timerContent(["startAt": true, "endAt": 1_600_000, "state": "running"])
      )
    ) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .timerBoundsInvalid)
    }
  }

  func testRejectsNonFiniteTimerBounds() {
    XCTAssertThrowsError(
      try LiveActivityContentParser.parse(
        timerContent(["startAt": 1_000_000, "endAt": Double.infinity, "state": "running"])
      )
    ) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .timerBoundsInvalid)
    }
  }

  func testRejectsEndBeforeStart() {
    XCTAssertThrowsError(
      try LiveActivityContentParser.parse(
        timerContent(["startAt": 1_600_000, "endAt": 1_000_000, "state": "running"])
      )
    ) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .timerEndBeforeStart)
    }
  }

  func testRejectsUnknownTimerState() {
    XCTAssertThrowsError(
      try LiveActivityContentParser.parse(
        timerContent(["startAt": 1_000_000, "endAt": 1_600_000, "state": "sleeping"])
      )
    ) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .timerStateInvalid)
    }
  }

  func testRejectsPausedWithoutPauseAt() {
    XCTAssertThrowsError(
      try LiveActivityContentParser.parse(
        timerContent(["startAt": 1_000_000, "endAt": 1_600_000, "state": "paused"])
      )
    ) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .timerPauseMissing)
    }
  }

  func testRejectsPauseOutsideInterval() {
    XCTAssertThrowsError(
      try LiveActivityContentParser.parse(
        timerContent([
          "startAt": 1_000_000, "endAt": 1_600_000,
          "pauseAt": 2_000_000, "state": "paused",
        ])
      )
    ) { error in
      XCTAssertEqual(error as? LiveActivityContentError, .timerPauseOutOfRange)
    }
  }
}
