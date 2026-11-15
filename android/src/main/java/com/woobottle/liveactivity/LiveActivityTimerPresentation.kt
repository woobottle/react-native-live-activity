package com.woobottle.liveactivity

import com.woobottle.liveactivity.LiveActivityContentParser.ParsedTimer
import com.woobottle.liveactivity.LiveActivityContentParser.TimerState

/** 알림의 timer 자리에 무엇을 그릴지. */
sealed class TimerDisplay {
  /** OS 크로노미터가 [untilMs]까지 카운트다운한다 (API 24+). */
  data class Countdown(val untilMs: Long) : TimerDisplay()

  /** 고정 텍스트. */
  data class Fixed(val text: String) : TimerDisplay()

  /** API 23 전용: 크로노미터 카운트다운이 없어 종료 시각을 시스템 시각 표시로 보여준다. */
  data class EndsAt(val atMs: Long) : TimerDisplay()
}

/**
 * @property nextTransitionAtMs 표시가 바뀌어야 하는 다음 시각. 이 시각에 알람으로
 *   알림을 다시 그린다. 더 이상 바뀔 일이 없으면 null.
 */
data class TimerPresentation(val display: TimerDisplay, val nextTransitionAtMs: Long?)

/**
 * timer와 현재 시각으로 알림 표시를 결정한다. Android 프레임워크에 의존하지 않아
 * JVM 유닛테스트가 가능하다.
 */
object LiveActivityTimerPresentation {

  private const val CHRONOMETER_COUNTDOWN_MIN_SDK = 24 // Build.VERSION_CODES.N
  private const val ZERO = "00:00"

  fun present(timer: ParsedTimer, nowMs: Long, sdkInt: Int): TimerPresentation =
    when (timer.state) {
      TimerState.PAUSED -> {
        // 파서가 paused에는 pauseAt을 보장한다.
        val pauseAt = timer.pauseAtMs ?: timer.endAtMs
        TimerPresentation(TimerDisplay.Fixed(format(timer.endAtMs - pauseAt)), null)
      }
      TimerState.COMPLETED -> TimerPresentation(TimerDisplay.Fixed(ZERO), null)
      TimerState.RUNNING -> when {
        nowMs < timer.startAtMs -> TimerPresentation(
          TimerDisplay.Fixed(format(timer.endAtMs - timer.startAtMs)),
          timer.startAtMs
        )
        nowMs < timer.endAtMs -> TimerPresentation(
          if (sdkInt >= CHRONOMETER_COUNTDOWN_MIN_SDK) {
            TimerDisplay.Countdown(timer.endAtMs)
          } else {
            TimerDisplay.EndsAt(timer.endAtMs)
          },
          timer.endAtMs
        )
        else -> TimerPresentation(TimerDisplay.Fixed(ZERO), null)
      }
    }

  /**
   * 알림 크로노미터(`DateUtils.formatElapsedTime`)와 같은 형식:
   * 1시간 미만 `mm:ss`, 이상 `h:mm:ss`. 초는 내림, 음수는 0.
   */
  fun format(durationMs: Long): String {
    val totalSeconds = maxOf(0L, durationMs) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
      "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
      "%02d:%02d".format(minutes, seconds)
    }
  }
}
