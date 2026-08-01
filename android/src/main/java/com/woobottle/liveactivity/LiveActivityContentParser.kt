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

  data class ParsedContent(
    val title: String,
    val subtitle: String?,
    val progress: Int?
  )

  fun parse(content: ReadableMap): ParsedContent = ParsedContent(
    title = content.requiredString("title"),
    subtitle = content.optionalString("subtitle"),
    progress = content.optionalProgress("progress")
  )

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
