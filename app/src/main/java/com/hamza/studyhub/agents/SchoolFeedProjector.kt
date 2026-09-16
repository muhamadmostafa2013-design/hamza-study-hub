package com.hamza.studyhub.agents

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Produces the parent-facing "current" school feed without deleting history.
 *
 * Historical rows stay in school_notifications.jsonl for audit/reference, but Home and the
 * Study Supervisor should not keep treating stale overdue rows as today's priorities forever.
 */
object SchoolFeedProjector {
    private const val OVERDUE_GRACE_DAYS = 2L
    private const val UNDATED_RECENT_DAYS = 7L
    private const val RECENT_CHANGE_HOURS = 48L

    fun current(
        feed: List<JSONObject>,
        today: LocalDate = LocalDate.now(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<JSONObject> {
        val latestByKey = linkedMapOf<String, JSONObject>()
        val unkeyed = mutableListOf<JSONObject>()

        feed.forEach { item ->
            val key = stableKey(item)
            if (key == null) {
                unkeyed += item
            } else {
                val previous = latestByKey[key]
                if (previous == null || item.optLong("timestamp", 0L) >= previous.optLong("timestamp", 0L)) {
                    latestByKey[key] = item
                }
            }
        }

        return (latestByKey.values + unkeyed)
            .filterNot { it.optBoolean("completed", false) }
            .filter { isCurrent(it, today, nowMillis) }
            .sortedByDescending { it.optLong("timestamp", 0L) }
    }

    private fun isCurrent(item: JSONObject, today: LocalDate, nowMillis: Long): Boolean {
        val dueDate = parseDueDate(item.optInt("dueDate", 0))
        val timestamp = item.optLong("timestamp", 0L)
        val recentlyChanged = timestamp > 0L && nowMillis - timestamp <= RECENT_CHANGE_HOURS * 60L * 60L * 1000L
        val explicitlyUpdated = item.optString("syncChange").equals("updated", ignoreCase = true)

        if (dueDate != null) {
            val daysFromToday = ChronoUnit.DAYS.between(today, dueDate)
            if (daysFromToday >= -OVERDUE_GRACE_DAYS) return true
            return recentlyChanged && explicitlyUpdated
        }

        if (timestamp <= 0L) return false
        val itemDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val ageDays = ChronoUnit.DAYS.between(itemDate, today)
        return ageDays in 0..UNDATED_RECENT_DAYS
    }

    private fun parseDueDate(value: Int): LocalDate? {
        if (value <= 0) return null
        return runCatching { LocalDate.parse(value.toString(), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
    }

    private fun stableKey(item: JSONObject): String? = sequenceOf(
        item.optString("externalId"),
        item.optString("contentFingerprint"),
        item.optString("notificationKey")
    ).firstOrNull { it.isNotBlank() }
}
