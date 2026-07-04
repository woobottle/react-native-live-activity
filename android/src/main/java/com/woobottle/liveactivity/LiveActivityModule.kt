package com.woobottle.liveactivity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.facebook.react.bridge.WritableNativeMap
import java.util.Collections
import java.util.UUID
import kotlin.math.roundToInt

class LiveActivityModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  // Activity ids currently backed by the foreground service, so update/end can
  // route to the service rather than the plain notification manager.
  private val foregroundActivityIds = Collections.synchronizedSet(mutableSetOf<String>())

  override fun getName(): String = NAME

  @ReactMethod
  fun isSupported(promise: Promise) {
    promise.resolve(hasNotificationPermission())
  }

  @ReactMethod
  fun getPlatformCapabilities(promise: Promise) {
    val capabilities = WritableNativeMap()
    capabilities.putBoolean("iosLiveActivity", false)
    capabilities.putBoolean("androidLiveUpdate", hasNotificationPermission())
    promise.resolve(capabilities)
  }

  @ReactMethod
  fun startActivity(content: ReadableMap, options: ReadableMap?, promise: Promise) {
    val activityId = UUID.randomUUID().toString()

    try {
      requireNotificationPermission()
      val parsed = parseContent(content)

      if (isForegroundRequested(options)) {
        LiveActivityForegroundService.start(
          context, activityId, parsed.title, parsed.subtitle, parsed.progress
        )
        foregroundActivityIds.add(activityId)
      } else {
        showNotification(activityId, parsed)
      }

      val result = WritableNativeMap()
      result.putString("activityId", activityId)
      promise.resolve(result)
    } catch (error: IllegalArgumentException) {
      promise.reject("E_INVALID_CONTENT", error.message, error)
    } catch (error: SecurityException) {
      promise.reject("E_NOTIFICATION_PERMISSION", "Notification permission is not granted.", error)
    } catch (error: Exception) {
      promise.reject("E_NOTIFICATION_FAILED", "Failed to start live activity notification.", error)
    }
  }

  @ReactMethod
  fun updateActivity(activityId: String, content: ReadableMap, promise: Promise) {
    try {
      requireValidActivityId(activityId)
      val parsed = parseContent(content)

      if (foregroundActivityIds.contains(activityId)) {
        LiveActivityForegroundService.start(
          context, activityId, parsed.title, parsed.subtitle, parsed.progress
        )
      } else {
        showNotification(activityId, parsed)
      }
      promise.resolve(null)
    } catch (error: IllegalArgumentException) {
      promise.reject("E_INVALID_ARGUMENT", error.message, error)
    } catch (error: SecurityException) {
      promise.reject("E_NOTIFICATION_PERMISSION", "Notification permission is not granted.", error)
    } catch (error: Exception) {
      promise.reject("E_NOTIFICATION_FAILED", "Failed to update live activity notification.", error)
    }
  }

  @ReactMethod
  fun endActivity(activityId: String, promise: Promise) {
    try {
      requireValidActivityId(activityId)
      if (foregroundActivityIds.remove(activityId)) {
        LiveActivityForegroundService.stop(context)
      } else {
        LiveActivityNotifications.notificationManager(context).cancel(
          LiveActivityNotifications.tag(activityId),
          LiveActivityNotifications.id(activityId)
        )
      }
      promise.resolve(null)
    } catch (error: IllegalArgumentException) {
      promise.reject("E_INVALID_ARGUMENT", error.message, error)
    } catch (error: Exception) {
      promise.reject("E_NOTIFICATION_FAILED", "Failed to end live activity notification.", error)
    }
  }

  private val context: ReactApplicationContext
    get() = reactApplicationContext

  private fun showNotification(activityId: String, content: ParsedContent) {
    requireValidActivityId(activityId)
    requireNotificationPermission()
    LiveActivityNotifications.ensureChannel(context)

    val notification =
      LiveActivityNotifications.build(context, content.title, content.subtitle, content.progress)
    LiveActivityNotifications.notificationManager(context).notify(
      LiveActivityNotifications.tag(activityId),
      LiveActivityNotifications.id(activityId),
      notification
    )
  }

  private data class ParsedContent(
    val title: String,
    val subtitle: String?,
    val progress: Int?
  )

  private fun parseContent(content: ReadableMap): ParsedContent = ParsedContent(
    title = content.requiredString("title"),
    subtitle = content.optionalString("subtitle"),
    progress = content.optionalProgress("progress")
  )

  private fun isForegroundRequested(options: ReadableMap?): Boolean {
    if (options == null || !options.hasKey("android") || options.isNull("android")) {
      return false
    }
    if (options.getType("android") != ReadableType.Map) {
      return false
    }
    val android = options.getMap("android") ?: return false
    if (!android.hasKey("foregroundService") ||
      android.isNull("foregroundService") ||
      android.getType("foregroundService") != ReadableType.Boolean
    ) {
      return false
    }
    return android.getBoolean("foregroundService")
  }

  private fun hasNotificationPermission(): Boolean {
    val hasPostNotificationPermission =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val notificationsEnabled =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
        LiveActivityNotifications.notificationManager(context).areNotificationsEnabled()

    return hasPostNotificationPermission && notificationsEnabled
  }

  private fun requireNotificationPermission() {
    if (!hasNotificationPermission()) {
      throw SecurityException("Notification permission is not granted.")
    }
  }

  private fun requireValidActivityId(activityId: String) {
    require(activityId.isNotBlank()) { "activityId must not be blank." }
  }

  private fun ReadableMap.requiredString(key: String): String {
    require(hasKey(key) && !isNull(key) && getType(key) == ReadableType.String) {
      "$key must be a string."
    }

    val value = getString(key)?.trim().orEmpty()
    require(value.isNotEmpty()) { "$key must not be blank." }
    return value
  }

  private fun ReadableMap.optionalString(key: String): String? {
    if (!hasKey(key) || isNull(key)) {
      return null
    }

    require(getType(key) == ReadableType.String) { "$key must be a string." }
    return getString(key)
  }

  private fun ReadableMap.optionalProgress(key: String): Int? {
    if (!hasKey(key) || isNull(key)) {
      return null
    }

    require(getType(key) == ReadableType.Number) { "$key must be a number." }
    val value = getDouble(key)
    require(value in 0.0..1.0) { "$key must be between 0 and 1." }
    return (value * LiveActivityNotifications.MAX_PROGRESS).roundToInt()
  }

  companion object {
    const val NAME = "LiveActivity"
  }
}
