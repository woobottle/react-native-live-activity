package com.woobottle.liveactivity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
   * @param timer what to show in the timer slot (header), or null for none.
   * @param token [LiveActivityTimerToken] stored in the notification's extras, or null.
   * @param snapshot [snapshotExtras] for a plain notification, so the activity can be
   *   recovered from it after the app process restarts; null for none.
   */
  fun build(
    context: Context,
    title: String,
    subtitle: String?,
    progress: Int?,
    timer: TimerDisplay? = null,
    token: String? = null,
    snapshot: Bundle? = null
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

    when (timer) {
      is TimerDisplay.Countdown -> {
        builder
          .setShowWhen(true)
          .setWhen(timer.untilMs)
          .setUsesChronometer(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          builder.setChronometerCountDown(true)
        }
      }
      is TimerDisplay.Fixed -> builder.setSubText(timer.text)
      is TimerDisplay.EndsAt -> builder.setShowWhen(true).setWhen(timer.atMs)
      null -> Unit
    }

    if (token != null) {
      builder.addExtras(Bundle().apply { putString(LiveActivityTimerToken.EXTRA, token) })
    }

    if (snapshot != null) {
      builder.addExtras(snapshot)
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      @Suppress("DEPRECATION")
      builder.setPriority(Notification.PRIORITY_DEFAULT)
    }

    return builder.build()
  }

  /** The extras that let [LiveActivitySnapshots] recover the activity from its notification. */
  internal fun snapshotExtras(referenceId: String?, content: Map<String, Any?>): Bundle =
    Bundle().apply {
      referenceId?.let { putString(LiveActivitySnapshots.EXTRA_REFERENCE_ID, it) }
      putString(LiveActivitySnapshots.EXTRA_CONTENT, LiveActivitySnapshots.encodeContent(content))
    }

  /** The snapshot extras a posted notification carries, to carry them over on a redraw. */
  internal fun snapshotExtras(posted: Notification): Bundle? {
    val extras = posted.extras ?: return null
    val content = extras.getString(LiveActivitySnapshots.EXTRA_CONTENT) ?: return null
    return Bundle().apply {
      extras.getString(LiveActivitySnapshots.EXTRA_REFERENCE_ID)?.let {
        putString(LiveActivitySnapshots.EXTRA_REFERENCE_ID, it)
      }
      putString(LiveActivitySnapshots.EXTRA_CONTENT, content)
    }
  }

  /** This app's notifications on screen, as [LiveActivitySnapshots.merge] reads them. */
  internal fun postedNotifications(context: Context): List<PostedNotification> =
    notificationManager(context).activeNotifications.map {
      val extras = it.notification.extras
      PostedNotification(
        tag = it.tag,
        referenceId = extras?.getString(LiveActivitySnapshots.EXTRA_REFERENCE_ID),
        contentJson = extras?.getString(LiveActivitySnapshots.EXTRA_CONTENT)
      )
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
