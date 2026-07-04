package com.woobottle.liveactivity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Shared builder for the ongoing "live update" notification. Extracted so both
 * [LiveActivityModule] (plain ongoing-notification path) and
 * [LiveActivityForegroundService] (foreground-service path) produce an identical
 * notification from primitive content values.
 */
internal object LiveActivityNotifications {
  const val CHANNEL_ID = "live_activity"
  const val MAX_PROGRESS = 100
  private const val CHANNEL_NAME = "Live Activity"
  private const val CHANNEL_DESCRIPTION = "Ongoing live activity updates"
  private const val NOTIFICATION_TAG_PREFIX = "live_activity:"

  fun tag(activityId: String): String = "$NOTIFICATION_TAG_PREFIX$activityId"

  fun id(activityId: String): Int = activityId.hashCode()

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    val manager = notificationManager(context)
    if (manager.getNotificationChannel(CHANNEL_ID) != null) {
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
    manager.createNotificationChannel(channel)
  }

  /**
   * @param progress already normalized to 0..[MAX_PROGRESS], or null for none.
   */
  fun build(
    context: Context,
    title: String,
    subtitle: String?,
    progress: Int?
  ): Notification {
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
      .setSmallIcon(notificationIcon(context))
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

  fun notificationManager(context: Context): NotificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private fun notificationIcon(context: Context): Int {
    val appIcon = context.applicationInfo.icon
    return if (appIcon != 0) appIcon else android.R.drawable.stat_notify_more
  }

  private fun immutablePendingIntentFlag(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
