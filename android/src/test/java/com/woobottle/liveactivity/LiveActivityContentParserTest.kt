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
}
