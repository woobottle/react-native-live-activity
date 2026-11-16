package com.woobottle.liveactivity

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.woobottle.liveactivity.LiveActivityContentParser.ParsedTimer

/**
 * timer 표시가 바뀌어야 하는 시각(시작 전 → 카운트다운, 카운트다운 → 00:00)에
 * [LiveActivityTimerReceiver]를 깨우는 알람을 관리한다. 활동 하나당 알람 하나이고,
 * 다시 예약하면 이전 알람을 덮어쓴다.
 *
 * 앱에 정확한 알람 권한이 있으면 정각에, 없으면 `setWindow`로 최대 약 10분 늦게
 * 실행된다. 라이브러리는 그 권한을 선언하지 않는다 — 필요한 앱이 직접 선언한다.
 */
internal object LiveActivityTimerScheduler {
  private const val TAG = "LiveActivityTimer"
  private const val INEXACT_WINDOW_MS = 10 * 60 * 1000L

  private const val EXTRA_ACTIVITY_ID = "activityId"
  private const val EXTRA_TITLE = "title"
  private const val EXTRA_SUBTITLE = "subtitle"
  private const val EXTRA_PROGRESS = "progress"
  private const val EXTRA_FOREGROUND = "foreground"

  data class Payload(
    val activityId: String,
    val title: String,
    val subtitle: String?,
    val progress: Int?,
    val timer: ParsedTimer,
    val foreground: Boolean,
    /** 이 알람을 건 게시의 [LiveActivityTimerToken]. */
    val token: String
  )

  /** [presentation]에 다음 전환이 있으면 예약하고, 없으면 기존 알람을 취소한다. */
  fun sync(context: Context, payload: Payload?, activityId: String, presentation: TimerPresentation?) {
    val at = presentation?.nextTransitionAtMs
    if (payload != null && at != null) {
      schedule(context, payload, at)
    } else {
      cancel(context, activityId)
    }
  }

  fun schedule(context: Context, payload: Payload, atMs: Long) {
    try {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      val pendingIntent = pendingIntent(context, payload.activityId, toIntent(context, payload))
      val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
      if (canExact) {
        try {
          alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pendingIntent)
          return
        } catch (error: SecurityException) {
          Log.w(TAG, "Exact alarm refused; falling back to a 10-minute window.", error)
        }
      }
      alarmManager.setWindow(AlarmManager.RTC_WAKEUP, atMs, INEXACT_WINDOW_MS, pendingIntent)
    } catch (error: Exception) {
      // 알림은 이미 게시됐다. 최악의 경우 카운트다운이 음수로 넘어갈 뿐이다.
      Log.w(TAG, "Failed to schedule the timer transition for ${payload.activityId}.", error)
    }
  }

  fun cancel(context: Context, activityId: String) {
    try {
      // 걸린 알람이 없으면 새로 만들지 않는다 (null).
      val pendingIntent = PendingIntent.getBroadcast(
        context,
        LiveActivityNotifications.id(activityId),
        baseIntent(context),
        PendingIntent.FLAG_NO_CREATE or immutableFlag()
      ) ?: return
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      alarmManager.cancel(pendingIntent)
      pendingIntent.cancel()
    } catch (error: Exception) {
      Log.w(TAG, "Failed to cancel the timer transition for $activityId.", error)
    }
  }

  fun readPayload(intent: Intent): Payload? {
    val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)
    val title = intent.getStringExtra(EXTRA_TITLE)
    val timer = LiveActivityTimerExtras.read(intent)
    val token = intent.getStringExtra(LiveActivityTimerToken.EXTRA)
    if (activityId.isNullOrBlank() || title.isNullOrBlank() || timer == null || token == null) {
      return null
    }
    val progress = intent.getIntExtra(EXTRA_PROGRESS, -1).takeIf { it >= 0 }
    return Payload(
      activityId = activityId,
      title = title,
      subtitle = intent.getStringExtra(EXTRA_SUBTITLE),
      progress = progress,
      timer = timer,
      foreground = intent.getBooleanExtra(EXTRA_FOREGROUND, false),
      token = token
    )
  }

  private fun baseIntent(context: Context): Intent =
    Intent(context, LiveActivityTimerReceiver::class.java)

  private fun toIntent(context: Context, payload: Payload): Intent =
    baseIntent(context).apply {
      putExtra(EXTRA_ACTIVITY_ID, payload.activityId)
      putExtra(EXTRA_TITLE, payload.title)
      putExtra(EXTRA_SUBTITLE, payload.subtitle)
      putExtra(EXTRA_PROGRESS, payload.progress ?: -1)
      putExtra(EXTRA_FOREGROUND, payload.foreground)
      putExtra(LiveActivityTimerToken.EXTRA, payload.token)
      LiveActivityTimerExtras.put(this, payload.timer)
    }

  // requestCode로 활동을 구분한다. extras는 PendingIntent 동일성 비교에 들어가지 않으므로
  // cancel()은 extras 없는 Intent로도 같은 알람을 찾는다.
  private fun pendingIntent(context: Context, activityId: String, intent: Intent): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      LiveActivityNotifications.id(activityId),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
    )

  private fun immutableFlag(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
