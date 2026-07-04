package com.woobottle.liveactivity

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.facebook.react.bridge.WritableNativeMap
import java.util.UUID
import kotlin.math.roundToInt

class LiveActivityModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = NAME

  @ReactMethod
  fun isSupported(promise: Promise) {
    promise.resolve(hasNotificationPermission())
  }

  @ReactMethod
  fun startActivity(content: ReadableMap, promise: Promise) {
    val activityId = UUID.randomUUID().toString()

    try {
      showNotification(activityId, content)

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
      showNotification(activityId, content)
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
      notificationManager.cancel(notificationTag(activityId), notificationId(activityId))
      promise.resolve(null)
    } catch (error: IllegalArgumentException) {
      promise.reject("E_INVALID_ARGUMENT", error.message, error)
    } catch (error: Exception) {
      promise.reject("E_NOTIFICATION_FAILED", "Failed to end live activity notification.", error)
    }
  }

  private val context: ReactApplicationContext
    get() = reactApplicationContext

  private val notificationManager: NotificationManager
    get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private fun showNotification(activityId: String, content: ReadableMap) {
    requireValidActivityId(activityId)
    requireNotificationPermission()
    createNotificationChannel()

    val notification = buildNotification(content)
    notificationManager.notify(notificationTag(activityId), notificationId(activityId), notification)
  }

  private fun buildNotification(content: ReadableMap): Notification {
    val title = content.requiredString("title")
    val subtitle = content.optionalString("subtitle")
    val progress = content.optionalProgress("progress")
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
      ?: Intent().setPackage(context.packageName)
    val pendingIntent = PendingIntent.getActivity(
      context,
      0,
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or immutablePendingIntentFlag()
    )
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(context, CHANNEL_ID)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(context)
    }

    builder
      .setSmallIcon(notificationIcon())
      .setContentTitle(title)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
      .setCategory(Notification.CATEGORY_STATUS)
      .setVisibility(Notification.VISIBILITY_PUBLIC)

    if (subtitle != null) {
      builder
        .setContentText(subtitle)
        .setStyle(Notification.BigTextStyle().bigText(subtitle))
    }

    if (progress != null) {
      builder.setProgress(MAX_PROGRESS, progress, false)
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      @Suppress("DEPRECATION")
      builder.setPriority(Notification.PRIORITY_DEFAULT)
    }

    return builder.build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
    if (existingChannel != null) {
      return
    }

    val channel = NotificationChannel(
      CHANNEL_ID,
      CHANNEL_NAME,
      NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
      setShowBadge(false)
      description = CHANNEL_DESCRIPTION
    }

    notificationManager.createNotificationChannel(channel)
  }

  private fun notificationIcon(): Int {
    val appIcon = context.applicationInfo.icon
    return if (appIcon != 0) appIcon else android.R.drawable.stat_notify_more
  }

  private fun immutablePendingIntentFlag(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

  private fun hasNotificationPermission(): Boolean {
    val hasPostNotificationPermission =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val notificationsEnabled =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.N || notificationManager.areNotificationsEnabled()

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

  private fun notificationTag(activityId: String): String = "$NOTIFICATION_TAG_PREFIX$activityId"

  private fun notificationId(activityId: String): Int = activityId.hashCode()

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
    return (value * MAX_PROGRESS).roundToInt()
  }

  companion object {
    const val NAME = "LiveActivity"
    private const val CHANNEL_ID = "live_activity"
    private const val CHANNEL_NAME = "Live Activity"
    private const val CHANNEL_DESCRIPTION = "Ongoing live activity updates"
    private const val MAX_PROGRESS = 100
    private const val NOTIFICATION_TAG_PREFIX = "live_activity:"
  }
}
