package com.hamza.studyhub.homework

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Read-only presentation rules for the homework screen.
 * School due dates and completion states are never rewritten by this layer.
 */
object HomeworkScreenPolicy {
    enum class Filter { TO_DO, REVIEW, ALL }
    enum class Tone { URGENT, WARNING, NORMAL, UNKNOWN }

    data class DueStatus(val text: String, val tone: Tone, val rank: Int, val date: LocalDate?)

    fun isHomework(item: JSONObject): Boolean =
        when (item.optString("updateType")) {
            "HOMEWORK" -> true
            "UNKNOWN", "DEADLINE_CHANGE", "TIMETABLE_CHANGE", "SCHOOL_MESSAGE" -> false
            else -> HomeworkInterpreter.interpret(
                item.optString("title"),
                item.optString("bigText").ifBlank { item.optString("text") }
            ).type == UpdateType.HOMEWORK
        }

    fun completed(item: JSONObject): Boolean =
        item.optBoolean("completed", false) || item.optInt("progressPercent", 0) >= 100

    fun needsReview(item: JSONObject): Boolean =
        !completed(item) && item.optBoolean("needsAttention", false) &&
            !item.optBoolean("attentionResolved", false)

    fun dueStatus(date: Int, today: LocalDate = LocalDate.now()): DueStatus {
        val parsed = if (date <= 0) null else runCatching {
            LocalDate.parse(date.toString(), DateTimeFormatter.BASIC_ISO_DATE)
        }.getOrNull()
        if (parsed == null) return DueStatus("الموعد غير محدد", Tone.UNKNOWN, 6, null)

        val days = ChronoUnit.DAYS.between(today, parsed)
        return when {
            days < -7 -> DueStatus("موعد قديم • تأكد", Tone.WARNING, 5, parsed)
            days < 0 -> DueStatus("متأخر ${-days} يوم", Tone.URGENT, 0, parsed)
            days == 0L -> DueStatus("التسليم اليوم", Tone.URGENT, 1, parsed)
            days == 1L -> DueStatus("التسليم غدًا", Tone.WARNING, 2, parsed)
            days in 2L..3L -> DueStatus("خلال $days أيام", Tone.WARNING, 3, parsed)
            else -> DueStatus("التسليم ${parsed.format(DateTimeFormatter.ofPattern("dd/MM"))}", Tone.NORMAL, 4, parsed)
        }
    }

    fun visible(
        feed: List<JSONObject>,
        filter: Filter,
        today: LocalDate = LocalDate.now()
    ): List<JSONObject> {
        val selected = when (filter) {
            Filter.TO_DO -> feed.filter { isHomework(it) && !completed(it) }
            Filter.REVIEW -> feed.filter(::needsReview)
            Filter.ALL -> feed
        }
        if (filter == Filter.ALL) return selected.sortedByDescending { it.optLong("timestamp", 0L) }
        return selected.sortedWith(
            compareBy<JSONObject> { dueStatus(it.optInt("dueDate", 0), today).rank }
                .thenBy { dueStatus(it.optInt("dueDate", 0), today).date ?: LocalDate.MAX }
                .thenByDescending { it.optLong("timestamp", 0L) }
        )
    }

    fun preview(text: String, limit: Int = 170): String {
        val compact = text.replace(Regex("\\s+"), " ").trim()
        if (compact.length <= limit) return compact
        val cut = compact.take(limit).substringBeforeLast(' ', missingDelimiterValue = compact.take(limit))
        return cut.trimEnd() + "…"
    }
}
