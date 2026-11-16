package com.woobottle.liveactivity

import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import kotlin.math.roundToInt

/**
 * JS에서 넘어온 content 페이로드의 파싱·검증.
 *
 * [LiveActivityModule]에서 떼어내 JVM 유닛테스트가 가능하도록 했다.
 * `internal`이 아니라 공개 `object`인 이유는 AGP의 friend-path 설정에 의존하지
 * 않고 테스트 소스셋에서 확실히 보이게 하기 위해서다.
 *
 * 검증 실패는 전부 [IllegalArgumentException] — 호출부가 이미 그 타입을
 * `E_INVALID_CONTENT` / `E_INVALID_ARGUMENT` reject로 변환한다.
 */
object LiveActivityContentParser {

  /** 알림 ProgressBar의 최대값. [LiveActivityNotifications.MAX_PROGRESS]와 같은 값이다. */
  const val MAX_PROGRESS = 100

  enum class TimerState(val jsValue: String) {
    RUNNING("running"),
    PAUSED("paused"),
    COMPLETED("completed");

    companion object {
      fun fromJs(value: String?): TimerState? = values().firstOrNull { it.jsValue == value }
    }
  }

  /** 검증을 통과한 timer. 시각은 epoch ms (소수 ms는 버림). */
  data class ParsedTimer(
    val startAtMs: Long,
    val endAtMs: Long,
    val pauseAtMs: Long?,
    val state: TimerState
  )

  data class ParsedContent(
    val title: String,
    val subtitle: String?,
    val progress: Int?,
    val timer: ParsedTimer? = null
  )

  fun parse(content: ReadableMap): ParsedContent = ParsedContent(
    title = content.requiredString("title"),
    subtitle = content.optionalString("subtitle"),
    progress = content.optionalProgress("progress"),
    timer = content.optionalTimer("timer")
  )

  // 메시지와 검증 순서는 ios/LiveActivityContentParser.swift의 parseTimer와 동일해야 한다.
  private const val TIMER_NOT_OBJECT = "content.timer must be an object"
  private const val TIMER_BOUNDS_INVALID = "content.timer startAt and endAt must be finite numbers"
  private const val TIMER_END_BEFORE_START = "content.timer endAt must be after startAt"
  private const val TIMER_STATE_INVALID = "content.timer state is invalid"
  private const val TIMER_PAUSE_MISSING = "content.timer pauseAt is required when paused"
  private const val TIMER_PAUSE_OUT_OF_RANGE = "content.timer pauseAt must be within the timer interval"

  private fun ReadableMap.optionalTimer(key: String): ParsedTimer? {
    if (!hasKey(key) || isNull(key)) {
      return null
    }
    require(getType(key) == ReadableType.Map) { TIMER_NOT_OBJECT }
    val timer = getMap(key) ?: throw IllegalArgumentException(TIMER_NOT_OBJECT)

    // 비교는 전부 원본 Double로 한다. 먼저 Long으로 내리면 iOS가 거부하는
    // 입력(예: startAt=100.7, endAt=100.3)을 통과시키게 된다.
    val startAt = timer.finiteNumber("startAt") ?: throw IllegalArgumentException(TIMER_BOUNDS_INVALID)
    val endAt = timer.finiteNumber("endAt") ?: throw IllegalArgumentException(TIMER_BOUNDS_INVALID)
    require(endAt >= startAt) { TIMER_END_BEFORE_START }

    val state = if (timer.hasKey("state") && !timer.isNull("state") && timer.getType("state") == ReadableType.String) {
      TimerState.fromJs(timer.getString("state"))
    } else {
      null
    } ?: throw IllegalArgumentException(TIMER_STATE_INVALID)

    val pauseAt = if (timer.hasKey("pauseAt") && !timer.isNull("pauseAt")) {
      timer.finiteNumber("pauseAt") ?: throw IllegalArgumentException(TIMER_BOUNDS_INVALID)
    } else {
      null
    }
    require(state != TimerState.PAUSED || pauseAt != null) { TIMER_PAUSE_MISSING }
    require(pauseAt == null || pauseAt in startAt..endAt) { TIMER_PAUSE_OUT_OF_RANGE }

    return ParsedTimer(startAt.toLong(), endAt.toLong(), pauseAt?.toLong(), state)
  }

  /** Number 타입이고 유한한 값만. Boolean은 ReadableType.Boolean이라 여기서 걸러진다. */
  private fun ReadableMap.finiteNumber(key: String): Double? {
    if (!hasKey(key) || isNull(key) || getType(key) != ReadableType.Number) {
      return null
    }
    return getDouble(key).takeIf { it.isFinite() }
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

    // ReadableType.Boolean은 Number와 구분되므로 JS `true`가 여기서 걸러진다.
    require(getType(key) == ReadableType.Number) { "$key must be a number." }
    val value = getDouble(key)
    require(value in 0.0..1.0) { "$key must be between 0 and 1." }
    return (value * MAX_PROGRESS).roundToInt()
  }
}
