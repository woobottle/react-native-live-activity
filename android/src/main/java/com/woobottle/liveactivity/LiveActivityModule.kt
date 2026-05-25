package com.woobottle.liveactivity

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap

class LiveActivityModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = NAME

  @ReactMethod
  fun isSupported(promise: Promise) {
    // The Android live update / promoted ongoing notification path is broadly
    // available across the project's supported Android versions. The scaffold
    // reports true so JS consumers can branch on platform without crashing;
    // a real implementation will validate channel + permission state.
    promise.resolve(true)
  }

  @ReactMethod
  fun startActivity(content: ReadableMap, promise: Promise) {
    promise.reject(
      "E_NOT_IMPLEMENTED",
      "startActivity native implementation has not landed yet. Tracked in TECH-PLAN phase 7."
    )
  }

  @ReactMethod
  fun updateActivity(activityId: String, content: ReadableMap, promise: Promise) {
    promise.reject(
      "E_NOT_IMPLEMENTED",
      "updateActivity native implementation has not landed yet. Tracked in TECH-PLAN phase 9."
    )
  }

  @ReactMethod
  fun endActivity(activityId: String, promise: Promise) {
    promise.reject(
      "E_NOT_IMPLEMENTED",
      "endActivity native implementation has not landed yet. Tracked in TECH-PLAN phase 9."
    )
  }

  companion object {
    const val NAME = "LiveActivity"
  }
}
