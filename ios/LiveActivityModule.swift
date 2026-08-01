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
    let parsed: LiveActivityParsedContent
    do {
      parsed = try LiveActivityContentParser.parse(content as? [AnyHashable: Any] ?? [:])
    } catch let error as LiveActivityContentError {
      reject("E_INVALID_CONTENT", error.message, nil)
      return nil
    } catch {
      reject("E_INVALID_CONTENT", "content is invalid", error)
      return nil
    }

    let state = LiveActivityAttributes.ContentState(
      title: parsed.title,
      subtitle: parsed.subtitle,
      progress: parsed.progress,
      timerStartAt: parsed.timer?.startAt,
      timerEndAt: parsed.timer?.endAt,
      timerPauseAt: parsed.timer?.pauseAt,
      timerState: parsed.timer?.state
    )

    // 실행 중인 타이머는 종료 시점 이후 stale로 표시해 위젯이 낡은 값을 계속
    // 보여주지 않게 한다.
    let staleDate = parsed.timer?.state == "running" ? parsed.timer?.endAt : nil
    return ParsedActivityContent(state: state, staleDate: staleDate)
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
        "startAt": LiveActivityContentParser.milliseconds(from: startAt),
        "endAt": LiveActivityContentParser.milliseconds(from: endAt),
        "state": timerState,
      ]
      if let pauseAt = state.timerPauseAt {
        timer["pauseAt"] = LiveActivityContentParser.milliseconds(from: pauseAt)
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
