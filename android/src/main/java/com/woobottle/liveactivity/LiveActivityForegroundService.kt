package com.woobottle.liveactivity

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Hosts a single long-running live activity as a foreground service so the
 * ongoing notification is less likely to be reclaimed by the system. Opt-in via
 * `startActivity(content, { android: { foregroundService: true } })`.
 *
 * v1 scope: one primary foreground activity at a time — starting another
 * foreground activity replaces the hosted notification. Non-foreground
 * activities are unaffected and continue via [LiveActivityModule].
 */
class LiveActivityForegroundService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopForegroundCompat()
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_START -> {
        val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)
        val title = intent.getStringExtra(EXTRA_TITLE)
        if (activityId.isNullOrBlank() || title.isNullOrBlank()) {
          stopSelf()
          return START_NOT_STICKY
        }
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE)
        val progress =
          if (intent.hasExtra(EXTRA_PROGRESS) && intent.getIntExtra(EXTRA_PROGRESS, -1) >= 0) {
            intent.getIntExtra(EXTRA_PROGRESS, 0)
          } else {
            null
          }

        LiveActivityNotifications.ensureChannel(this)
        val notification = LiveActivityNotifications.build(this, title, subtitle, progress)
        startInForeground(LiveActivityNotifications.id(activityId), notification)
        return START_STICKY
      }
      else -> {
        stopSelf()
        return START_NOT_STICKY
      }
    }
  }

  private fun startInForeground(notificationId: Int, notification: android.app.Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      startForeground(notificationId, notification)
    }
  }

  private fun stopForegroundCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(true)
    }
  }

  companion object {
    private const val ACTION_START = "com.woobottle.liveactivity.action.START"
    private const val ACTION_STOP = "com.woobottle.liveactivity.action.STOP"
    private const val EXTRA_ACTIVITY_ID = "activityId"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_SUBTITLE = "subtitle"
    private const val EXTRA_PROGRESS = "progress"

    /** Start or update the foreground activity with the given content. */
    fun start(
      context: Context,
      activityId: String,
      title: String,
      subtitle: String?,
      progress: Int?
    ) {
      val intent = Intent(context, LiveActivityForegroundService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_ACTIVITY_ID, activityId)
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_SUBTITLE, subtitle)
        putExtra(EXTRA_PROGRESS, progress ?: -1)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    /** Stop the foreground service. */
    fun stop(context: Context) {
      val intent = Intent(context, LiveActivityForegroundService::class.java).apply {
        action = ACTION_STOP
      }
      context.startService(intent)
    }
  }
}
