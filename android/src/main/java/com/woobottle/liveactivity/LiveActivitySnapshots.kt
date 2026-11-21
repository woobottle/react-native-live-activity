package com.woobottle.liveactivity

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** `getActiveActivities()`가 돌려주는 활동 하나. */
internal data class ActivitySnapshot(
  val activityId: String,
  val referenceId: String?,
  val content: HashMap<String, Any>
)

/** 화면에 떠 있는 알림 하나에서 읽은 값. */
internal data class PostedNotification(
  val tag: String?,
  val referenceId: String?,
  val contentJson: String?
)

/**
 * 앱 프로세스가 재시작돼도 활동을 되찾을 수 있도록, 일반 알림에 referenceId와
 * content(JSON)를 심고 다시 읽는다. activityId는 알림 tag에 이미 들어 있다.
 * Android 프레임워크에 의존하지 않아 JVM 유닛테스트가 가능하다.
 */
internal object LiveActivitySnapshots {

  const val EXTRA_REFERENCE_ID = "com.woobottle.liveactivity.referenceId"
  const val EXTRA_CONTENT = "com.woobottle.liveactivity.content"

  private const val TAG_PREFIX = "live_activity:"

  fun encodeContent(content: Map<String, Any?>): String = toJson(content).toString()

  /** 깨진 JSON이면 null. 숫자는 모두 Double로, null 값은 뺀다. */
  fun decodeContent(json: String): HashMap<String, Any>? =
    try {
      fromJson(JSONObject(json))
    } catch (error: JSONException) {
      null
    }

  fun activityIdFromTag(tag: String?): String? =
    tag?.takeIf { it.startsWith(TAG_PREFIX) }
      ?.removePrefix(TAG_PREFIX)
      ?.takeIf { it.isNotEmpty() }

  /**
   * 메모리 목록에, 화면에 있지만 목록에 없는 활동을 더한다. 같은 id면 메모리 쪽이 이긴다.
   * content를 읽을 수 없는 알림(이 라이브러리 것이 아니거나 이전 버전이 올린 것)은 뺀다.
   */
  fun merge(known: Collection<ActivitySnapshot>, posted: List<PostedNotification>): List<ActivitySnapshot> {
    val knownIds = known.mapTo(HashSet()) { it.activityId }
    val recovered = posted.mapNotNull { notification ->
      val activityId = activityIdFromTag(notification.tag) ?: return@mapNotNull null
      if (activityId in knownIds) return@mapNotNull null
      val content = notification.contentJson?.let(::decodeContent) ?: return@mapNotNull null
      knownIds.add(activityId)
      ActivitySnapshot(activityId, notification.referenceId, content)
    }
    return known.toList() + recovered
  }

  private fun toJson(map: Map<*, *>): JSONObject {
    val json = JSONObject()
    map.forEach { (key, value) ->
      if (key is String && value != null) json.put(key, toJsonValue(value))
    }
    return json
  }

  private fun toJsonValue(value: Any): Any = when (value) {
    is Map<*, *> -> toJson(value)
    is List<*> -> JSONArray().apply { value.forEach { item -> put(item?.let(::toJsonValue) ?: JSONObject.NULL) } }
    else -> value
  }

  private fun fromJson(json: JSONObject): HashMap<String, Any> {
    val map = HashMap<String, Any>()
    json.keys().forEach { key ->
      fromJsonValue(json.get(key))?.let { map[key] = it }
    }
    return map
  }

  private fun fromJsonValue(value: Any): Any? = when (value) {
    JSONObject.NULL -> null
    is JSONObject -> fromJson(value)
    is JSONArray -> ArrayList<Any?>().apply { for (i in 0 until value.length()) add(fromJsonValue(value.get(i))) }
    is Number -> value.toDouble()
    else -> value
  }
}
