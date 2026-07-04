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

    guard let state = Self.makeContentState(from: content, rejecter: reject) else {
      return
    }

    do {
      let activity = try Activity<LiveActivityAttributes>.request(
        attributes: LiveActivityAttributes(),
        contentState: state,
        pushType: nil
      )
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

    guard let state = Self.makeContentState(from: content, rejecter: reject) else {
      return
    }

    let id = activityId as String
    guard let activity = Self.findActivity(id: id) else {
      reject("E_NOT_FOUND", "No active Live Activity found for id \(id)", nil)
      return
    }

    Task {
      if #available(iOS 16.2, *) {
        await activity.update(ActivityContent(state: state, staleDate: nil))
      } else {
        await activity.update(using: state)
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
  private static func makeContentState(
    from content: NSDictionary,
    rejecter reject: RCTPromiseRejectBlock
  ) -> LiveActivityAttributes.ContentState? {
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

    return LiveActivityAttributes.ContentState(
      title: title,
      subtitle: subtitle,
      progress: normalizedProgress
    )
  }
}
