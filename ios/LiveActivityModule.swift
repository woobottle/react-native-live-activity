import Foundation
import ActivityKit
import React

// `LiveActivityAttributes` is defined in `LiveActivityAttributes.swift` so the
// exact same type can be compiled into the app's Widget Extension target, which
// renders the Live Activity UI. See the README "iOS Widget Extension setup".

@objc(LiveActivity)
class LiveActivityModule: NSObject {

  @objc static func requiresMainQueueSetup() -> Bool {
    return false
  }

  // MARK: - isSupported

  @objc func isSupported(_ resolve: @escaping RCTPromiseResolveBlock,
                         rejecter reject: @escaping RCTPromiseRejectBlock) {
    if #available(iOS 16.1, *) {
      resolve(ActivityAuthorizationInfo().areActivitiesEnabled)
    } else {
      resolve(false)
    }
  }

  // MARK: - getPlatformCapabilities

  @objc func getPlatformCapabilities(_ resolve: @escaping RCTPromiseResolveBlock,
                                     rejecter reject: @escaping RCTPromiseRejectBlock) {
    var iosLiveActivity = false
    if #available(iOS 16.1, *) {
      iosLiveActivity = ActivityAuthorizationInfo().areActivitiesEnabled
    }
    resolve([
      "iosLiveActivity": iosLiveActivity,
      "androidLiveUpdate": false,
    ])
  }

  // MARK: - getActiveActivities

  @objc func getActiveActivities(_ resolve: @escaping RCTPromiseResolveBlock,
                                 rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard #available(iOS 16.1, *) else {
      resolve([])
      return
    }

    let activities = Activity<LiveActivityAttributes>.activities.map { activity in
      let state: LiveActivityAttributes.ContentState
      if #available(iOS 16.2, *) {
        state = activity.content.state
      } else {
        state = activity.contentState
      }

      return Self.serialize(
        activityId: activity.id,
        referenceId: activity.attributes.referenceId,
        state: state
      )
    }
    resolve(activities)
  }

  // MARK: - startActivity

  // `options` (e.g. Android's foregroundService) is accepted for a uniform
  // cross-platform bridge signature but has no effect on iOS.
  @objc func startActivity(_ content: NSDictionary,
                           options: NSDictionary,
                           resolver resolve: @escaping RCTPromiseResolveBlock,
                           rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard #available(iOS 16.1, *) else {
      reject("E_UNSUPPORTED", "Live Activity requires iOS 16.1 or newer", nil)
      return
    }

    guard ActivityAuthorizationInfo().areActivitiesEnabled else {
      reject("E_DISABLED", "Live Activities are disabled on this device", nil)
      return
    }

    guard let parsed = Self.makeContent(from: content, rejecter: reject) else {
      return
    }

    let referenceId = options["referenceId"] as? String
    let attributes = LiveActivityAttributes(referenceId: referenceId)

    do {
      let activity: Activity<LiveActivityAttributes>
      if #available(iOS 16.2, *) {
        activity = try Activity<LiveActivityAttributes>.request(
          attributes: attributes,
          content: ActivityContent(
            state: parsed.state,
            staleDate: parsed.staleDate
          ),
          pushType: nil
        )
      } else {
        activity = try Activity<LiveActivityAttributes>.request(
          attributes: attributes,
          contentState: parsed.state,
          pushType: nil
        )
      }
      resolve(["activityId": activity.id])
    } catch {
      reject("E_START_FAILED", "Failed to start Live Activity", error)
    }
  }

  // MARK: - updateActivity

  @objc func updateActivity(_ activityId: NSString,
                            content: NSDictionary,
                            resolver resolve: @escaping RCTPromiseResolveBlock,
                            rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard #available(iOS 16.1, *) else {
      reject("E_UNSUPPORTED", "Live Activity requires iOS 16.1 or newer", nil)
      return
    }

    guard let parsed = Self.makeContent(from: content, rejecter: reject) else {
      return
    }

    let id = activityId as String
    guard let activity = Self.findActivity(id: id) else {
      reject("E_NOT_FOUND", "No active Live Activity found for id \(id)", nil)
      return
    }

    Task {
      if #available(iOS 16.2, *) {
        await activity.update(
          ActivityContent(
            state: parsed.state,
            staleDate: parsed.staleDate
          )
        )
      } else {
        await activity.update(using: parsed.state)
      }
      resolve(nil)
    }
  }

  // MARK: - endActivity

  @objc func endActivity(_ activityId: NSString,
                         resolver resolve: @escaping RCTPromiseResolveBlock,
                         rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard #available(iOS 16.1, *) else {
      reject("E_UNSUPPORTED", "Live Activity requires iOS 16.1 or newer", nil)
      return
    }

    let id = activityId as String
    guard let activity = Self.findActivity(id: id) else {
      reject("E_NOT_FOUND", "No active Live Activity found for id \(id)", nil)
      return
    }

    Task {
      if #available(iOS 16.2, *) {
        let finalState = activity.content.state
        await activity.end(
          ActivityContent(state: finalState, staleDate: nil),
          dismissalPolicy: .immediate
        )
      } else {
        await activity.end(dismissalPolicy: .immediate)
      }
      resolve(nil)
    }
  }

  @available(iOS 16.1, *)
  private static func findActivity(id: String) -> Activity<LiveActivityAttributes>? {
    Activity<LiveActivityAttributes>.activities.first { $0.id == id }
  }

  @available(iOS 16.1, *)
  private struct ParsedActivityContent {
    let state: LiveActivityAttributes.ContentState
    let staleDate: Date?
  }

  @available(iOS 16.1, *)
  private static func makeContent(
    from content: NSDictionary,
    rejecter reject: RCTPromiseRejectBlock
  ) -> ParsedActivityContent? {
    guard let title = content["title"] as? String, !title.isEmpty else {
      reject("E_INVALID_CONTENT", "content.title must be a non-empty string", nil)
      return nil
    }

    let subtitle = content["subtitle"] as? String
    let progress = content["progress"] as? NSNumber
    let normalizedProgress = progress?.doubleValue

    if let normalizedProgress = normalizedProgress {
      if normalizedProgress < 0 || normalizedProgress > 1 {
        reject("E_INVALID_CONTENT", "content.progress must be between 0 and 1", nil)
        return nil
      }
    }

    var parsedTimer: ParsedTimer?
    if let timerValue = content["timer"] {
      guard let timer = makeTimer(from: timerValue, rejecter: reject) else {
        return nil
      }
      parsedTimer = timer
    }

    let state = LiveActivityAttributes.ContentState(
      title: title,
      subtitle: subtitle,
      progress: normalizedProgress,
      timerStartAt: parsedTimer?.startAt,
      timerEndAt: parsedTimer?.endAt,
      timerPauseAt: parsedTimer?.pauseAt,
      timerState: parsedTimer?.state
    )

    let staleDate = parsedTimer?.state == "running" ? parsedTimer?.endAt : nil
    return ParsedActivityContent(state: state, staleDate: staleDate)
  }

  private struct ParsedTimer {
    let startAt: Date
    let endAt: Date
    let pauseAt: Date?
    let state: String
  }

  private static func makeTimer(
    from value: Any,
    rejecter reject: RCTPromiseRejectBlock
  ) -> ParsedTimer? {
    guard let timer = value as? NSDictionary else {
      reject("E_INVALID_CONTENT", "content.timer must be an object", nil)
      return nil
    }
    guard
      let startMilliseconds = timer["startAt"] as? NSNumber,
      let endMilliseconds = timer["endAt"] as? NSNumber,
      startMilliseconds.doubleValue.isFinite,
      endMilliseconds.doubleValue.isFinite
    else {
      reject("E_INVALID_CONTENT", "content.timer startAt and endAt must be finite numbers", nil)
      return nil
    }

    let startAt = date(fromMilliseconds: startMilliseconds)
    let endAt = date(fromMilliseconds: endMilliseconds)
    guard endAt >= startAt else {
      reject("E_INVALID_CONTENT", "content.timer endAt must be after startAt", nil)
      return nil
    }

    guard
      let state = timer["state"] as? String,
      ["running", "paused", "completed"].contains(state)
    else {
      reject("E_INVALID_CONTENT", "content.timer state is invalid", nil)
      return nil
    }

    var pauseAt: Date?
    if let pauseMilliseconds = timer["pauseAt"] as? NSNumber {
      guard pauseMilliseconds.doubleValue.isFinite else {
        reject("E_INVALID_CONTENT", "content.timer pauseAt must be a finite number", nil)
        return nil
      }
      pauseAt = date(fromMilliseconds: pauseMilliseconds)
    }

    if state == "paused" && pauseAt == nil {
      reject("E_INVALID_CONTENT", "content.timer pauseAt is required when paused", nil)
      return nil
    }
    if let pauseAt, !(startAt...endAt).contains(pauseAt) {
      reject("E_INVALID_CONTENT", "content.timer pauseAt must be within the timer interval", nil)
      return nil
    }

    return ParsedTimer(
      startAt: startAt,
      endAt: endAt,
      pauseAt: pauseAt,
      state: state
    )
  }

  private static func date(fromMilliseconds value: NSNumber) -> Date {
    Date(timeIntervalSince1970: value.doubleValue / 1_000)
  }

  private static func milliseconds(from date: Date) -> Double {
    date.timeIntervalSince1970 * 1_000
  }

  @available(iOS 16.1, *)
  private static func serialize(
    activityId: String,
    referenceId: String?,
    state: LiveActivityAttributes.ContentState
  ) -> [String: Any] {
    var content: [String: Any] = ["title": state.title]
    if let subtitle = state.subtitle {
      content["subtitle"] = subtitle
    }
    if let progress = state.progress {
      content["progress"] = progress
    }
    if
      let startAt = state.timerStartAt,
      let endAt = state.timerEndAt,
      let timerState = state.timerState
    {
      var timer: [String: Any] = [
        "startAt": milliseconds(from: startAt),
        "endAt": milliseconds(from: endAt),
        "state": timerState,
      ]
      if let pauseAt = state.timerPauseAt {
        timer["pauseAt"] = milliseconds(from: pauseAt)
      }
      content["timer"] = timer
    }

    var serialized: [String: Any] = [
      "activityId": activityId,
      "content": content,
    ]
    if let referenceId {
      serialized["referenceId"] = referenceId
    }
    return serialized
  }
}
