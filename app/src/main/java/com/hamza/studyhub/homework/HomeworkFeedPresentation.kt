package com.hamza.studyhub.homework

import com.hamza.studyhub.agents.SchoolFeedProjector
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pure decision layer for the one-glance homework screen.
 * Never fabricates a due date, drops the archived feed, or marks school work submitted.
 */
object HomeworkFeedPresentation {
    fun isHomework(item: JSONObject): Boolean {
        val type = item.optString("updateType")
        if (type.isNotBlank()) return type == UpdateType.HOMEWORK.name
        return HomeworkInterpreter.interpret(
            item.optString("title"),
            item.optString("bigText").ifBlank { item.optString("text") }
        ).type == UpdateType.HOMEWORK
    }

    fun isFinished(item: JSONObject): Boolean =
        item.optBoolean("completed", false) || item.optInt("progressPercent", 0) >= 100

    fun isAttention(item: JSONObject): Boolean =
        item.optBoolean("needsAttention", false) && !item.optBoolean("attentionResolved", false)

    fun dueDate(item: JSONObject): LocalDate? =
        item.optInt("dueDate", 0).takeIf { it > 0 }?.let { value ->
            runCatching { LocalDate.parse(value.toString(), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
        }

    fun dueRank(item: JSONObject, today: LocalDate = LocalDate.now()): Int {
        if (isFinished(item)) return 5
        val due = dueDate(item) ?: return 4
        return when {
            due.isBefore(today) -> 0
            due.isEqual(today) -> 1
            due.isEqual(today.plusDays(1)) -> 2
            else -> 3
        }
    }

    fun isUrgent(item: JSONObject, today: LocalDate = LocalDate.now()): Boolean {
        if (isFinished(item)) return false
        val due = dueDate(item) ?: return false
        return !due.isAfter(today)
    }

    fun deadlineLabel(item: JSONObject, today: LocalDate = LocalDate.now()): String {
        if (isFinished(item)) return "مكتمل داخل التطبيق"
        val due = dueDate(item) ?: return "موعد غير محدد"
        return when {
            due.isBefore(today) -> "متأخر • ${due.format(DateTimeFormatter.ofPattern("dd/MM"))}"
            due.isEqual(today) -> "التسليم اليوم"
            due.isEqual(today.plusDays(1)) -> "التسليم بكرة"
            else -> "التسليم ${due.format(DateTimeFormatter.ofPattern("dd/MM"))}"
        }
    }

    fun key(item: JSONObject): String = sequenceOf(
        item.optString("externalId"),
        item.optString("notificationKey"),
        item.optString("contentFingerprint")
    ).firstOrNull { it.isNotBlank() }
        ?: "${item.optLong("timestamp")}-${item.optString("title")}"

    fun currentHomework(
        feed: List<JSONObject>,
        today: LocalDate = LocalDate.now(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<JSONObject> =
        sort(SchoolFeedProjector.current(feed, today, nowMillis).filter(::isHomework), today)

    fun currentAttention(
        feed: List<JSONObject>,
        today: LocalDate = LocalDate.now(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<JSONObject> =
        sort(SchoolFeedProjector.current(feed, today, nowMillis).filter(::isAttention), today)

    private fun sort(items: List<JSONObject>, today: LocalDate): List<JSONObject> =
        items.sortedWith(
            compareBy<JSONObject> { dueRank(it, today) }
                .thenBy { dueDate(it) ?: LocalDate.MAX }
                .thenByDescending { it.optLong("timestamp", 0L) }
        )

    fun preview(item: JSONObject, limit: Int = 140): String =
        item.optString("bigText").ifBlank { item.optString("text") }
            .replace(Regex("\\s+"), " ").trim()
            .take(limit).ifBlank { "تفاصيل الواجب غير متاحة؛ افتح المصدر للتحقق منها." }

    fun nextStep(item: JSONObject): JSONObject? {
        val tasks = item.optJSONArray("taskChecklist") ?: return null
        for (i in 0 until tasks.length()) {
            val step = tasks.optJSONObject(i) ?: continue
            if (step.optString("id").isNotBlank() && !step.optBoolean("done", false)) return step
        }
        return null
    }
}
