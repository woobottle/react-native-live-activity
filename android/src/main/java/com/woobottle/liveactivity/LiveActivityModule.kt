package com.woobottle.liveactivity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableNativeMap
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LiveActivityModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  // Activity ids currently backed by the foreground service, so update/end can
  // route to the service rather than the plain notification manager.
  private val foregroundActivityIds = Collections.synchronizedSet(mutableSetOf<String>())
  private val activeSnapshots = ConcurrentHashMap<String, ActivitySnapshot>()

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
  fun getActiveActivities(promise: Promise) {
    val activities = WritableNativeArray()
    activeSnapshotsWithRecovered().forEach { snapshot ->
      val activity = WritableNativeMap()
      activity.putString("activityId", snapshot.activityId)
      snapshot.referenceId?.let { activity.putString("referenceId", it) }
      activity.putMap("content", Arguments.makeNativeMap(snapshot.content))
      activities.pushMap(activity)
    }
    promise.resolve(activities)
  }

  @ReactMethod
  fun startActivity(content: ReadableMap, options: ReadableMap?, promise: Promise) {
    val activityId = UUID.randomUUID().toString()

    try {
      requireNotificationPermission()
      val parsed = LiveActivityContentParser.parse(content)

      val referenceId = options.optionalReferenceId()
      val rawContent = content.toHashMap()
      if (isForegroundRequested(options)) {
        LiveActivityForegroundService.start(
          context, activityId, parsed.title, parsed.subtitle, parsed.progress, parsed.timer
        )
        foregroundActivityIds.add(activityId)
      } else {
        showNotification(activityId, parsed, referenceId, rawContent)
      }

      activeSnapshots[activityId] = ActivitySnapshot(
        activityId = activityId,
        referenceId = referenceId,
        content = rawContent
      )
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
      val parsed = LiveActivityContentParser.parse(content)

      val rawContent = content.toHashMap()
      if (foregroundActivityIds.contains(activityId)) {
        LiveActivityForegroundService.start(
          context, activityId, parsed.title, parsed.subtitle, parsed.progress, parsed.timer
        )
        activeSnapshots.computeIfPresent(activityId) { _, snapshot ->
          snapshot.copy(content = rawContent)
        }
      } else {
        // An activity missing from the map may be one recovered from its notification
        // after a restart: keep the referenceId the notification carries.
        val referenceId = activeSnapshots[activityId]?.referenceId ?: postedReferenceId(activityId)
        showNotification(activityId, parsed, referenceId, rawContent)
        activeSnapshots[activityId] = ActivitySnapshot(activityId, referenceId, rawContent)
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
      // 알림을 내리기 전에 알람부터 취소한다.
      LiveActivityTimerScheduler.cancel(context, activityId)
      if (foregroundActivityIds.remove(activityId)) {
        LiveActivityForegroundService.stop(context)
      } else {
        LiveActivityNotifications.notificationManager(context).cancel(
          LiveActivityNotifications.tag(activityId),
          LiveActivityNotifications.id(activityId)
        )
      }
      activeSnapshots.remove(activityId)
      promise.resolve(null)
    } catch (error: IllegalArgumentException) {
      promise.reject("E_INVALID_ARGUMENT", error.message, error)
    } catch (error: Exception) {
      promise.reject("E_NOTIFICATION_FAILED", "Failed to end live activity notification.", error)
    }
  }

  private val context: ReactApplicationContext
    get() = reactApplicationContext

  /**
   * The in-memory snapshots plus, after a process restart, the activities whose plain
   * notifications are still on screen. Recovered ones join the map.
   */
  private fun activeSnapshotsWithRecovered(): List<ActivitySnapshot> {
    val posted = try {
      LiveActivityNotifications.postedNotifications(context)
    } catch (error: Exception) {
      return activeSnapshots.values.toList()
    }
    val merged = LiveActivitySnapshots.merge(activeSnapshots.values.toList(), posted)
    merged.forEach { activeSnapshots.putIfAbsent(it.activityId, it) }
    return merged
  }

  private fun postedReferenceId(activityId: String): String? =
    try {
      LiveActivityNotifications.postedNotifications(context)
        .firstOrNull { it.tag == LiveActivityNotifications.tag(activityId) }
        ?.referenceId
    } catch (error: Exception) {
      null
    }

  private fun showNotification(
    activityId: String,
    content: LiveActivityContentParser.ParsedContent,
    referenceId: String?,
    rawContent: Map<String, Any?>
  ) {
    requireValidActivityId(activityId)
    requireNotificationPermission()
    LiveActivityNotifications.ensureChannel(context)

    val timer = content.timer?.let {
      LiveActivityTimerPresentation.present(it, System.currentTimeMillis(), Build.VERSION.SDK_INT)
    }
    val token = content.timer?.let { LiveActivityTimerToken.next() }
    val notification = LiveActivityNotifications.build(
      context, content.title, content.subtitle, content.progress, timer?.display, token,
      snapshot = LiveActivityNotifications.snapshotExtras(referenceId, rawContent)
    )
    LiveActivityNotifications.notificationManager(context).notify(
      LiveActivityNotifications.tag(activityId),
      LiveActivityNotifications.id(activityId),
      notification
    )

    // 알림을 게시한 쪽이 전환 알람도 정한다 (포그라운드 경로는 서비스가 한다).
    val payload = if (content.timer != null && token != null) {
      LiveActivityTimerScheduler.Payload(
        activityId, content.title, content.subtitle, content.progress, content.timer, foreground = false, token
      )
    } else {
      null
    }
    LiveActivityTimerScheduler.sync(context, payload, activityId, timer)
  }

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

  private fun ReadableMap?.optionalReferenceId(): String? {
    if (this == null ||
      !hasKey("referenceId") ||
      isNull("referenceId") ||
      getType("referenceId") != ReadableType.String
    ) {
      return null
    }
    return getString("referenceId")
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

  companion object {
    const val NAME = "LiveActivity"
  }
}
