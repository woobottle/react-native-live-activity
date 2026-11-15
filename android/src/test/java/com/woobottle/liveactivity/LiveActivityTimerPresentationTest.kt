package com.woobottle.liveactivity

import com.woobottle.liveactivity.LiveActivityContentParser.ParsedTimer
import com.woobottle.liveactivity.LiveActivityContentParser.TimerState
import com.woobottle.liveactivity.LiveActivityTimerPresentation.format
import com.woobottle.liveactivity.LiveActivityTimerPresentation.present
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale

class LiveActivityTimerPresentationTest {

  private val start = 0L
  private val end = 600_000L // 10분

  private fun timer(state: TimerState, pauseAt: Long? = null) = ParsedTimer(start, end, pauseAt, state)

  @Test
  fun `before start shows the full duration`() {
    assertEquals(
      TimerPresentation(TimerDisplay.Fixed("10:00"), 0L),
      present(timer(TimerState.RUNNING), nowMs = -5_000, sdkInt = 34)
    )
  }

  @Test
  fun `running counts down to endAt`() {
    assertEquals(
      TimerPresentation(TimerDisplay.Countdown(600_000), 600_000L),
      present(timer(TimerState.RUNNING), nowMs = 1_000, sdkInt = 34)
    )
  }

  @Test
  fun `running starts counting down exactly at startAt`() {
    assertEquals(
      TimerPresentation(TimerDisplay.Countdown(600_000), 600_000L),
      present(timer(TimerState.RUNNING), nowMs = 0, sdkInt = 34)
    )
  }

  @Test
  fun `zero uses the same locale digits as every other fixed text`() {
    val original = Locale.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag("ar-EG"))
      val zero = format(0)
      assumeTrue("JDK has no native digits for ar-EG", zero != "00:00")
      assertEquals(TimerDisplay.Fixed(zero), present(timer(TimerState.COMPLETED), nowMs = 1_000, sdkInt = 34).display)
      assertEquals(TimerDisplay.Fixed(zero), present(timer(TimerState.RUNNING), nowMs = 600_000, sdkInt = 34).display)
    } finally {
      Locale.setDefault(original)
    }
  }

  @Test
  fun `running on API 23 shows the end time`() {
    assertEquals(
      TimerPresentation(TimerDisplay.EndsAt(600_000), 600_000L),
      present(timer(TimerState.RUNNING), nowMs = 1_000, sdkInt = 23)
    )
  }

  @Test
  fun `running past endAt freezes at zero`() {
    assertEquals(
      TimerPresentation(TimerDisplay.Fixed("00:00"), null),
      present(timer(TimerState.RUNNING), nowMs = 600_000, sdkInt = 34)
    )
  }

  @Test
  fun `running ignores pauseAt`() {
    assertEquals(
      TimerDisplay.Countdown(600_000),
      present(timer(TimerState.RUNNING, pauseAt = 300_000), nowMs = 1_000, sdkInt = 34).display
    )
  }

  @Test
  fun `paused shows the remaining time`() {
    val expected = TimerPresentation(TimerDisplay.Fixed("06:39"), null)
    assertEquals(expected, present(timer(TimerState.PAUSED, pauseAt = 200_500), nowMs = 1_000, sdkInt = 34))
    assertEquals(expected, present(timer(TimerState.PAUSED, pauseAt = 200_500), nowMs = 1_000, sdkInt = 23))
  }

  @Test
  fun `completed shows zero`() {
    assertEquals(
      TimerPresentation(TimerDisplay.Fixed("00:00"), null),
      present(timer(TimerState.COMPLETED), nowMs = 1_000, sdkInt = 34)
    )
  }

  @Test
  fun `format pads minutes below an hour`() {
    assertEquals("00:59", format(59_900))
    assertEquals("00:00", format(0))
    assertEquals("09:59", format(599_999))
  }

  @Test
  fun `format adds hours from one hour`() {
    assertEquals("1:00:00", format(3_600_000))
    assertEquals("10:01:01", format(36_061_000))
  }

  @Test
  fun `format clamps negatives to zero`() {
    assertEquals("00:00", format(-1))
  }
}
