package com.woobottle.liveactivity

import com.facebook.react.bridge.JavaOnlyMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JavaOnlyMap은 순수 Java ReadableMap 구현이라 Android 프레임워크 없이
 * JVM 유닛테스트에서 그대로 쓸 수 있다.
 */
class LiveActivityContentParserTest {

  private fun content(vararg pairs: Pair<String, Any?>): JavaOnlyMap =
    JavaOnlyMap.of(*pairs.flatMap { listOf(it.first, it.second) }.toTypedArray())

  @Test
  fun `parses minimal content`() {
    val parsed = LiveActivityContentParser.parse(content("title" to "Delivery"))
    assertEquals("Delivery", parsed.title)
    assertNull(parsed.subtitle)
    assertNull(parsed.progress)
  }

  @Test
  fun `trims the title`() {
    val parsed = LiveActivityContentParser.parse(content("title" to "  Delivery  "))
    assertEquals("Delivery", parsed.title)
  }

  @Test
  fun `rejects a missing title`() {
    assertThrows(IllegalArgumentException::class.java) {
      LiveActivityContentParser.parse(content("subtitle" to "x"))
    }
  }

  @Test
  fun `rejects a blank title`() {
    assertThrows(IllegalArgumentException::class.java) {
      LiveActivityContentParser.parse(content("title" to "   "))
    }
  }

  @Test
  fun `rejects a non-string title`() {
    assertThrows(IllegalArgumentException::class.java) {
      LiveActivityContentParser.parse(content("title" to 42))
    }
  }

  @Test
  fun `scales progress to the notification range`() {
    assertEquals(0, LiveActivityContentParser.parse(content("title" to "t", "progress" to 0.0)).progress)
    assertEquals(50, LiveActivityContentParser.parse(content("title" to "t", "progress" to 0.5)).progress)
    assertEquals(100, LiveActivityContentParser.parse(content("title" to "t", "progress" to 1.0)).progress)
  }

  @Test
  fun `treats a null progress as absent`() {
    assertNull(LiveActivityContentParser.parse(content("title" to "t", "progress" to null)).progress)
  }

  @Test
  fun `rejects progress out of range`() {
    assertThrows(IllegalArgumentException::class.java) {
      LiveActivityContentParser.parse(content("title" to "t", "progress" to 1.5))
    }
    assertThrows(IllegalArgumentException::class.java) {
      LiveActivityContentParser.parse(content("title" to "t", "progress" to -0.1))
    }
  }

  @Test
  fun `rejects a boolean progress`() {
    assertThrows(IllegalArgumentException::class.java) {
      LiveActivityContentParser.parse(content("title" to "t", "progress" to true))
    }
  }

  @Test
  fun `rejects a non-string subtitle`() {
    assertThrows(IllegalArgumentException::class.java) {
      LiveActivityContentParser.parse(content("title" to "t", "subtitle" to 7))
    }
  }

  // ---- timer ----

  private fun timer(vararg pairs: Pair<String, Any?>): JavaOnlyMap = content(*pairs)

  private fun parseTimer(timerValue: Any?): LiveActivityContentParser.ParsedTimer? =
    LiveActivityContentParser.parse(content("title" to "t", "timer" to timerValue)).timer

  private fun timerError(timerValue: Any?): String? =
    assertThrows(IllegalArgumentException::class.java) { parseTimer(timerValue) }.message

  private val running = arrayOf<Pair<String, Any?>>("startAt" to 1000.0, "endAt" to 61000.0, "state" to "running")

  @Test
  fun `parses no timer as null`() {
    assertNull(LiveActivityContentParser.parse(content("title" to "t")).timer)
    assertNull(parseTimer(null))
  }

  @Test
  fun `parses a running timer`() {
    assertEquals(
      LiveActivityContentParser.ParsedTimer(1000, 61000, null, LiveActivityContentParser.TimerState.RUNNING),
      parseTimer(timer("startAt" to 1000.0, "endAt" to 61000.9, "state" to "running"))
    )
  }

  @Test
  fun `parses paused and completed states`() {
    assertEquals(
      LiveActivityContentParser.ParsedTimer(1000, 61000, 2000, LiveActivityContentParser.TimerState.PAUSED),
      parseTimer(timer("startAt" to 1000.0, "endAt" to 61000.0, "pauseAt" to 2000.0, "state" to "paused"))
    )
    assertEquals(
      LiveActivityContentParser.TimerState.COMPLETED,
      parseTimer(timer("startAt" to 1000.0, "endAt" to 61000.0, "state" to "completed"))?.state
    )
  }

  @Test
  fun `accepts endAt equal to startAt`() {
    assertEquals(1000L, parseTimer(timer("startAt" to 1000.0, "endAt" to 1000.0, "state" to "running"))?.endAtMs)
  }

  @Test
  fun `accepts pauseAt on either boundary`() {
    assertEquals(1000L, parseTimer(timer("startAt" to 1000.0, "endAt" to 61000.0, "pauseAt" to 1000.0, "state" to "paused"))?.pauseAtMs)
    assertEquals(61000L, parseTimer(timer("startAt" to 1000.0, "endAt" to 61000.0, "pauseAt" to 61000.0, "state" to "paused"))?.pauseAtMs)
  }

  @Test
  fun `rejects a non-object timer`() {
    assertEquals("content.timer must be an object", timerError("x"))
  }

  @Test
  fun `rejects missing or boolean bounds`() {
    val message = "content.timer startAt and endAt must be finite numbers"
    assertEquals(message, timerError(timer("endAt" to 61000.0, "state" to "running")))
    assertEquals(message, timerError(timer("startAt" to true, "endAt" to 61000.0, "state" to "running")))
  }

  @Test
  fun `rejects non-finite bounds`() {
    val message = "content.timer startAt and endAt must be finite numbers"
    assertEquals(message, timerError(timer("startAt" to 1000.0, "endAt" to Double.NaN, "state" to "running")))
    assertEquals(message, timerError(timer("startAt" to 1000.0, "endAt" to Double.POSITIVE_INFINITY, "state" to "running")))
  }

  @Test
  fun `rejects endAt before startAt`() {
    assertEquals(
      "content.timer endAt must be after startAt",
      timerError(timer("startAt" to 2000.0, "endAt" to 1000.0, "state" to "running"))
    )
  }

  @Test
  fun `compares bounds before truncating`() {
    assertEquals(
      "content.timer endAt must be after startAt",
      timerError(timer("startAt" to 100.7, "endAt" to 100.3, "state" to "running"))
    )
  }

  @Test
  fun `rejects an unknown state`() {
    val message = "content.timer state is invalid"
    assertEquals(message, timerError(timer("startAt" to 1000.0, "endAt" to 61000.0, "state" to "stopped")))
    assertEquals(message, timerError(timer("startAt" to 1000.0, "endAt" to 61000.0, "state" to 1.0)))
  }

  @Test
  fun `rejects a non-numeric pauseAt`() {
    assertEquals(
      "content.timer startAt and endAt must be finite numbers",
      timerError(timer(*running, "pauseAt" to "x"))
    )
  }

  @Test
  fun `rejects paused without pauseAt`() {
    assertEquals(
      "content.timer pauseAt is required when paused",
      timerError(timer("startAt" to 1000.0, "endAt" to 61000.0, "state" to "paused"))
    )
  }

  @Test
  fun `rejects pauseAt outside the interval`() {
    assertEquals(
      "content.timer pauseAt must be within the timer interval",
      timerError(timer(*running, "pauseAt" to 61001.0))
    )
  }
}
