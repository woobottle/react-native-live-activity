package com.woobottle.liveactivity

import android.content.Intent
import com.woobottle.liveactivity.LiveActivityContentParser.ParsedTimer
import com.woobottle.liveactivity.LiveActivityContentParser.TimerState

/**
 * [ParsedTimer]를 Intent extras로 싣고 되읽는다. 포그라운드 서비스 Intent와
 * 전환 알람의 브로드캐스트 Intent가 같이 쓴다.
 */
internal object LiveActivityTimerExtras {
  private const val START_AT = "timerStartAtMs"
  private const val END_AT = "timerEndAtMs"
  private const val PAUSE_AT = "timerPauseAtMs"
  private const val STATE = "timerState"

  fun put(intent: Intent, timer: ParsedTimer?) {
    if (timer == null) {
      return
    }
    intent.putExtra(START_AT, timer.startAtMs)
    intent.putExtra(END_AT, timer.endAtMs)
    intent.putExtra(STATE, timer.state.name)
    timer.pauseAtMs?.let { intent.putExtra(PAUSE_AT, it) }
  }

  fun read(intent: Intent): ParsedTimer? {
    if (!intent.hasExtra(START_AT) || !intent.hasExtra(END_AT)) {
      return null
    }
    val state = intent.getStringExtra(STATE)
      ?.let { name -> TimerState.values().firstOrNull { it.name == name } }
      ?: return null
    return ParsedTimer(
      startAtMs = intent.getLongExtra(START_AT, 0L),
      endAtMs = intent.getLongExtra(END_AT, 0L),
      pauseAtMs = if (intent.hasExtra(PAUSE_AT)) intent.getLongExtra(PAUSE_AT, 0L) else null,
      state = state
    )
  }
}
