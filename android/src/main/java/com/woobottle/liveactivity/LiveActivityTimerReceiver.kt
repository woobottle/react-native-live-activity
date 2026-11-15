package com.woobottle.liveactivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * [LiveActivityTimerScheduler]가 건 알람을 받아 알림의 timer 표시를 다시 그린다.
 * 앱 프로세스가 죽어 있어도 Intent extras만으로 알림을 처음부터 만든다.
 *
 * 알림이 이미 사라졌으면(사용자가 지움, End됨, 포그라운드 서비스 종료) 아무것도
 * 하지 않는다 — 사라진 알림을 되살리지 않는다.
 */
class LiveActivityTimerReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val payload = LiveActivityTimerScheduler.readPayload(intent) ?: return
    try {
      val manager = LiveActivityNotifications.notificationManager(context)
      val id = LiveActivityNotifications.id(payload.activityId)
      // 포그라운드 서비스는 startForeground(id)로 tag 없이 게시한다.
      val tag = if (payload.foreground) null else LiveActivityNotifications.tag(payload.activityId)
      val stillShowing = manager.activeNotifications.any { it.id == id && it.tag == tag }
      if (!stillShowing) {
        return
      }

      val presentation = LiveActivityTimerPresentation.present(
        payload.timer, System.currentTimeMillis(), Build.VERSION.SDK_INT
      )
      val notification = LiveActivityNotifications.build(
        context, payload.title, payload.subtitle, payload.progress, presentation.display
      )
      if (tag == null) {
        manager.notify(id, notification)
      } else {
        manager.notify(tag, id, notification)
      }
      presentation.nextTransitionAtMs?.let {
        LiveActivityTimerScheduler.schedule(context, payload, it)
      }
    } catch (error: Exception) {
      Log.w("LiveActivityTimer", "Failed to apply the timer transition for ${payload.activityId}.", error)
    }
  }
}
