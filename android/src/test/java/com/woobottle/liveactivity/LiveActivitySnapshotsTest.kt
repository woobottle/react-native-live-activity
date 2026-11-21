package com.woobottle.liveactivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 앱 프로세스가 재시작돼도 화면에 남은 알림에서 활동을 되찾을 수 있도록,
 * 알림에 심어 둔 content와 referenceId를 읽고 메모리 목록과 합치는 규칙.
 */
class LiveActivitySnapshotsTest {

  private fun content(vararg pairs: Pair<String, Any>) = hashMapOf(*pairs)

  @Test
  fun `content round-trips with a nested timer`() {
    val original = content(
      "title" to "집중 10분",
      "subtitle" to "x",
      "progress" to 0.5,
      "timer" to hashMapOf<String, Any>("startAt" to 1.0E12, "endAt" to 1.0000006E12, "state" to "running")
    )

    val decoded = LiveActivitySnapshots.decodeContent(LiveActivitySnapshots.encodeContent(original))

    assertEquals(original, decoded)
    assertTrue(decoded!!["progress"] is Double)
    @Suppress("UNCHECKED_CAST")
    assertTrue((decoded["timer"] as Map<String, Any>)["startAt"] is Double)
  }

  @Test
  fun `null values are dropped`() {
    val decoded = LiveActivitySnapshots.decodeContent(
      LiveActivitySnapshots.encodeContent(mapOf("title" to "a", "subtitle" to null))
    )
    assertEquals(hashMapOf<String, Any>("title" to "a"), decoded)
  }

  @Test
  fun `malformed json decodes to null`() {
    assertNull(LiveActivitySnapshots.decodeContent("not json"))
  }

  @Test
  fun `activity id comes from the live activity tag`() {
    assertEquals("abc", LiveActivitySnapshots.activityIdFromTag("live_activity:abc"))
    assertNull(LiveActivitySnapshots.activityIdFromTag(null))
    assertNull(LiveActivitySnapshots.activityIdFromTag("other:abc"))
    assertNull(LiveActivitySnapshots.activityIdFromTag("live_activity:"))
  }

  @Test
  fun `merge adds on-screen activities the map lacks`() {
    val known = ActivitySnapshot("a", "ref-a", content("title" to "known"))
    val posted = listOf(
      PostedNotification("live_activity:a", "ref-a", LiveActivitySnapshots.encodeContent(content("title" to "posted"))),
      PostedNotification("live_activity:b", "ref-b", LiveActivitySnapshots.encodeContent(content("title" to "b")))
    )

    assertEquals(
      listOf(known, ActivitySnapshot("b", "ref-b", content("title" to "b"))),
      LiveActivitySnapshots.merge(listOf(known), posted)
    )
  }

  @Test
  fun `merge skips posts without a usable content extra`() {
    val posted = listOf(
      PostedNotification("live_activity:no-content", null, null),
      PostedNotification("live_activity:bad-json", null, "not json"),
      PostedNotification(null, null, LiveActivitySnapshots.encodeContent(content("title" to "fgs"))),
      PostedNotification("other:x", null, LiveActivitySnapshots.encodeContent(content("title" to "other")))
    )

    assertEquals(emptyList<ActivitySnapshot>(), LiveActivitySnapshots.merge(emptyList(), posted))
  }

  @Test
  fun `merge never brings back an activity ended in this process`() {
    // cancel()은 비동기라, 방금 끝낸 활동의 알림이 아직 목록에 남아 있을 수 있다.
    val posted = listOf(
      PostedNotification("live_activity:ended", null, LiveActivitySnapshots.encodeContent(content("title" to "ended")))
    )

    assertEquals(
      emptyList<ActivitySnapshot>(),
      LiveActivitySnapshots.merge(emptyList(), posted, ended = setOf("ended"))
    )
  }
}
