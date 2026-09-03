package com.hamza.studyhub.progress

import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Computes a parent-friendly snapshot from canonical homework records.
 * Later this can be backed by Room/Supabase without changing the metrics contract.
 */
object ProgressAnalytics {

    data class Snapshot(
        val homeworkCount: Int,
        val completedCount: Int,
        val completionRate: Int,
        val needsAttentionCount: Int,
        val overdueCount: Int,
        val inProgressCount: Int,
        val bySubject: Map<String, SubjectProgress>
    )

    data class SubjectProgress(
        val total: Int,
        val completed: Int,
        val completionRate: Int,
        val needsAttention: Int
    )

    fun fromFeed(file: File, today: LocalDate = LocalDate.now()): Snapshot {
        val items = if (!file.exists()) emptyList() else file.readLines()
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .filter { it.optString("updateType") == "HOMEWORK" }

        val completed = items.count { it.optBoolean("completed", false) || it.optInt("progressPercent", 0) >= 100 }
        val attention = items.count { it.optBoolean("needsAttention", false) && !it.optBoolean("attentionResolved", false) }
        val overdue = items.count { item ->
            if (item.optBoolean("completed", false)) return@count false
            parseUntisDate(item.optInt("dueDate", 0))?.isBefore(today) == true
        }
        val inProgress = items.count { it.optInt("progressPercent", 0) in 1..99 }

        val bySubject = items
            .groupBy { it.optString("subject").ifBlank { inferSubjectFromTitle(it.optString("title")) } }
            .mapValues { (_, subjectItems) ->
                val subjectCompleted = subjectItems.count {
                    it.optBoolean("completed", false) || it.optInt("progressPercent", 0) >= 100
                }
                val subjectAttention = subjectItems.count {
                    it.optBoolean("needsAttention", false) && !it.optBoolean("attentionResolved", false)
                }
                SubjectProgress(
                    total = subjectItems.size,
                    completed = subjectCompleted,
                    completionRate = rate(subjectCompleted, subjectItems.size),
                    needsAttention = subjectAttention
                )
            }

        return Snapshot(
            homeworkCount = items.size,
            completedCount = completed,
            completionRate = rate(completed, items.size),
            needsAttentionCount = attention,
            overdueCount = overdue,
            inProgressCount = inProgress,
            bySubject = bySubject
        )
    }

    private fun rate(done: Int, total: Int): Int =
        if (total == 0) 0 else ((done.toDouble() / total) * 100).toInt()

    private fun parseUntisDate(value: Int): LocalDate? {
        if (value <= 0) return null
        return runCatching {
            LocalDate.parse(value.toString(), DateTimeFormatter.BASIC_ISO_DATE)
        }.getOrNull()
    }

    private fun inferSubjectFromTitle(title: String): String =
        title.substringAfter("•", "غير محدد").trim().ifBlank { "غير محدد" }
}
