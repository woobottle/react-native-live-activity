package com.woobottle.liveactivity

import java.util.UUID

/**
 * 알림을 게시할 때마다 새 토큰을 알림 extras와 전환 알람 extras 양쪽에 싣는다.
 * 알람이 울렸을 때 떠 있는 알림의 토큰이 다르면, 그 사이 앱이 새 내용으로 다시
 * 게시한 것이므로 옛 내용으로 덮어쓰지 않는다. 토큰이 알림 자체에 들어 있어
 * 프로세스가 죽어 있어도 별도 저장소 없이 판정할 수 있다.
 */
object LiveActivityTimerToken {
  const val EXTRA = "com.woobottle.liveactivity.timerToken"

  fun next(): String = UUID.randomUUID().toString()

  fun matches(posted: String?, armed: String?): Boolean = armed != null && posted == armed
}
