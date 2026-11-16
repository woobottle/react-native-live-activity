package com.woobottle.liveactivity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 알람이 걸린 뒤 알림이 새 내용으로 다시 게시됐으면, 그 알람은 옛 내용을 들고 있으므로
 * 다시 그리면 안 된다. 게시된 알림의 토큰과 알람의 토큰이 같을 때만 다시 그린다.
 */
class LiveActivityTimerTokenTest {

  @Test
  fun `redraws only the notification the alarm was armed for`() {
    assertTrue(LiveActivityTimerToken.matches(posted = "a", armed = "a"))
  }

  @Test
  fun `skips a notification reposted with newer content`() {
    assertFalse(LiveActivityTimerToken.matches(posted = "b", armed = "a"))
  }

  @Test
  fun `skips when either side has no token`() {
    assertFalse(LiveActivityTimerToken.matches(posted = null, armed = "a"))
    assertFalse(LiveActivityTimerToken.matches(posted = "a", armed = null))
    assertFalse(LiveActivityTimerToken.matches(posted = null, armed = null))
  }

  @Test
  fun `issues a different token for every post`() {
    assertNotEquals(LiveActivityTimerToken.next(), LiveActivityTimerToken.next())
  }
}
