import Foundation
import React

@objc(LiveActivity)
class LiveActivityModule: NSObject {

  @objc static func requiresMainQueueSetup() -> Bool {
    return false
  }

  // MARK: - isSupported

  @objc func isSupported(_ resolve: @escaping RCTPromiseResolveBlock,
                         rejecter reject: @escaping RCTPromiseRejectBlock) {
    if #available(iOS 16.1, *) {
      // Real implementation will inspect ActivityAuthorizationInfo().areActivitiesEnabled
      // and account for user opt-out. Scaffold reports OS-level availability only.
      resolve(true)
    } else {
      resolve(false)
    }
  }

  // MARK: - startActivity

  @objc func startActivity(_ content: NSDictionary,
                           resolver resolve: @escaping RCTPromiseResolveBlock,
                           rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard #available(iOS 16.1, *) else {
      reject("E_UNSUPPORTED", "Live Activity requires iOS 16.1 or newer", nil)
      return
    }
    reject("E_NOT_IMPLEMENTED",
           "startActivity native implementation has not landed yet. Tracked in TECH-PLAN phase 6.",
           nil)
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
    reject("E_NOT_IMPLEMENTED",
           "updateActivity native implementation has not landed yet. Tracked in TECH-PLAN phase 8.",
           nil)
  }

  // MARK: - endActivity

  @objc func endActivity(_ activityId: NSString,
                         resolver resolve: @escaping RCTPromiseResolveBlock,
                         rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard #available(iOS 16.1, *) else {
      reject("E_UNSUPPORTED", "Live Activity requires iOS 16.1 or newer", nil)
      return
    }
    reject("E_NOT_IMPLEMENTED",
           "endActivity native implementation has not landed yet. Tracked in TECH-PLAN phase 8.",
           nil)
  }
}
